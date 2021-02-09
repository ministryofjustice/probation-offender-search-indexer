package uk.gov.justice.digital.hmpps.indexer.integration.e2e

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.until
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.ElasticsearchStatusException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.indexer.helpers.hasLogMessageContaining
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.integration.readResourceAsText
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension.Companion.communityApi
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.IndexService

class OffenderUpdateMessageTest : IntegrationTestBase() {

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @Nested
  inner class OffenderIndexesOk {
    @BeforeEach
    fun bothIndexesOK() {
      initialiseIndexStatus()
      communityApi.stubAllOffenderGets(10, numberOfOffenders = 0)
      buildAndSwitchIndex(SyncIndex.GREEN, 0)
    }

    @Test
    fun `Offender is saved and can be searched for`() {
      communityApi.stubGetOffender()

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { communityApi.getCountFor("/secure/offenders/crn/X123456/all") } matches { it == 1 }
      await untilCallTo { indexService.getIndexCount(SyncIndex.GREEN) } matches { it == 1L }

      val response = searchByCrn("X123456")

      communityApi.verifyGetOffender()
      assertThat(response.hits.asList()).extracting<String> { it.id }.containsExactly("X123456")
    }

    @Test
    fun `Offender is saved to both complete and building index`() {
      communityApi.stubAllOffenderGets(10, "X12345")
      buildAndSwitchIndex(SyncIndex.BLUE, 1)
      communityApi.stubGetOffender()

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { communityApi.getCountFor("/secure/offenders/crn/X123456/all") } matches { it == 1 }
      await untilCallTo { indexService.getIndexCount(SyncIndex.GREEN) } matches { it == 1L }
      await untilCallTo { indexService.getIndexCount(SyncIndex.BLUE) } matches { it == 2L }

      val responseGreen = searchByCrn("X123456", SyncIndex.GREEN)
      val responseBlue = searchByCrn("X123456", SyncIndex.BLUE)

      communityApi.verifyGetOffender()
      assertThat(responseGreen.hits.asList()).extracting<String> { it.id }.containsExactly("X123456")
      assertThat(responseBlue.hits.asList()).extracting<String> { it.id }.containsExactly("X123456")
    }
  }

  @Nested
  inner class NoOffenderIndexes {
    @BeforeEach
    fun noIndexesExist() {
      deleteOffenderIndexes()
      initialiseIndexStatus()
    }

    @Test
    fun `Indexes absent, no update`() {
      val indexServiceLogAppender = findLogAppender(IndexService::class.java)
      communityApi.stubGetOffender()

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { indexServiceLogAppender.list } matches { it hasLogMessageContaining "Ignoring update of offender" }

      assertThatThrownBy { searchByCrn("X123456") }
        .isInstanceOf(ElasticsearchStatusException::class.java)
        .hasMessageContaining("no such index")
      communityApi.verifyNotGetOffender("X123456")
    }
  }

  @Nested
  inner class OffenderNotFound {
    @BeforeEach
    fun singleIndexExists() {
      initialiseIndexStatus()
      communityApi.stubAllOffenderGets(10, numberOfOffenders = 0)
      buildAndSwitchIndex(SyncIndex.GREEN, 0)
    }

    @Test
    fun `Single index then offender is not added to either index`() {
      communityApi.stubOffenderNotFound("X123456")

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { communityApi.getCountFor("/secure/offenders/crn/X123456/all") } matches { it == 1 }

      val responseGreen = searchByCrn("X123456", SyncIndex.GREEN)
      val responseBlue = searchByCrn("X123456", SyncIndex.BLUE)

      assertThat(responseGreen.hits.asList()).isEmpty()
      assertThat(responseBlue.hits.asList()).isEmpty()
    }

    @Test
    fun `Both indexes OK then offender is not added to either index`() {
      communityApi.stubAllOffenderGets(10, "X12345")
      buildAndSwitchIndex(SyncIndex.BLUE, 1)
      communityApi.stubOffenderNotFound("X123456")

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { communityApi.getCountFor("/secure/offenders/crn/X123456/all") } matches { it == 1 }

      val responseGreen = searchByCrn("X123456", SyncIndex.GREEN)
      val responseBlue = searchByCrn("X123456", SyncIndex.BLUE)

      assertThat(responseGreen.hits.asList()).isEmpty()
      assertThat(responseBlue.hits.asList()).isEmpty()
    }
  }

  @Nested
  inner class MappaDetails {
    @BeforeEach
    fun createIndexes() {
      initialiseIndexStatus()
      communityApi.stubAllOffenderGets(10, numberOfOffenders = 0)
      buildAndSwitchIndex(SyncIndex.GREEN, 0)
    }

    private fun checkDocumentUpdated(node: String, expected: Any?): Boolean =
      try {
        val searchResult = searchByCrn("X123456").hits.asList()[0].sourceAsString
        assertThatJson(searchResult).node(node).isEqualTo(expected)
        true
      } catch (t: Throwable) {
        false
      }

    @Test
    fun `New offender with MAPPA - MAPPA details are returned`() {
      communityApi.stubGetOffender("X123456")
      communityApi.stubGetMappaDetails("X123456", notes = "Created")
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await until { checkDocumentUpdated("mappa.notes", "Created") }
      val result = searchByCrn("X123456").hits.asList()[0].sourceAsString
      assertThatJson(result).node("mappa.level").isEqualTo(1)
      assertThatJson(result).node("mappa.startDate").isEqualTo("2021-02-08")
      assertThatJson(result).node("mappa.team.code").isEqualTo("N02AAM")
    }

    @Test
    fun `Existing offender with MAPPA - updated MAPPA details are returned`() {
      // Given there is a saved offender with MAPPA details
      communityApi.stubGetOffender("X123456")
      communityApi.stubGetMappaDetails("X123456")
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())
      await untilCallTo { indexService.getIndexCount(SyncIndex.GREEN) } matches { it == 1L }

      // When I change the MAPPA details
      communityApi.resetAll()
      communityApi.stubGetOffender("X123456")
      communityApi.stubGetMappaDetails(crn = "X123456", level = 2, teamCode = "NEWTEAM", notes = "Updated")
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      // Then the MAPPA details are returned from a search
      await until { checkDocumentUpdated("mappa.notes", "Updated") }
      val result = searchByCrn("X123456").hits.asList()[0].sourceAsString
      assertThatJson(result).node("mappa.level").isEqualTo(2)
      assertThatJson(result).node("mappa.team.code").isEqualTo("NEWTEAM")
    }

    @Test
    fun `Existing offender with MAPPA - MAPPA is removed`() {
      // Given there is a saved offender with MAPPA details
      communityApi.stubGetOffender("X123456")
      communityApi.stubGetMappaDetails("X123456")
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())
      await untilCallTo { indexService.getIndexCount(SyncIndex.GREEN) } matches { it == 1L }

      // When I save the offender again without MAPPA details
      communityApi.resetAll()
      communityApi.stubGetOffender("X123456")
      communityApi.stubMappaNotFound("X123456")
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      // Then the mappa details will be removed from Elasticsearch
      await until { checkDocumentUpdated("mappa", null) }
    }


    @Test
    fun `Existing offender without MAPPA - new MAPPA details are returned`() {
      // Given there is a saved offender without MAPPA details
      communityApi.stubGetOffender("X123456")
      communityApi.stubMappaNotFound("X123456")
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())
      await untilCallTo { indexService.getIndexCount(SyncIndex.GREEN) } matches { it == 1L }

      // When I save the offender again with MAPPA details
      communityApi.resetAll()
      communityApi.stubGetOffender("X123456")
      communityApi.stubGetMappaDetails("X123456", level = 2, teamCode = "NEWTEAM", notes = "Updated")
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      // Then the MAPPA details are returned from a search
      await until { checkDocumentUpdated("mappa.notes", "Updated") }
      val result = searchByCrn("X123456").hits.asList()[0].sourceAsString
      assertThatJson(result).node("mappa.level").isEqualTo(2)
      assertThatJson(result).node("mappa.team.code").isEqualTo("NEWTEAM")
    }

  }
}
