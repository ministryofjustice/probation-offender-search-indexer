package uk.gov.justice.digital.hmpps.indexer.integration.e2e

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.ElasticsearchStatusException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.integration.findLogAppender
import uk.gov.justice.digital.hmpps.indexer.integration.hasLogMessageContaining
import uk.gov.justice.digital.hmpps.indexer.integration.readResourceAsText
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.IndexService

class OffenderUpdateMessageTest : IntegrationTestBase() {

  @Nested
  inner class OffenderIndexesOk {
    @BeforeEach
    fun initialise() {
      deleteOffenderIndexes()
      createOffenderIndexes()
      initialiseIndexStatus()
      buildInitialIndex()
    }

    @Test
    fun `Offender is saved and can be searched for`() {
      CommunityApiExtension.communityApi.stubGetOffender()

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { CommunityApiExtension.communityApi.getCountFor("/secure/offenders/crn/X123456/all") } matches { it == 1 }
      await untilCallTo { indexService.getIndexCount(SyncIndex.GREEN) } matches { it == 1L }

      val response = searchByCrn("X123456")

      CommunityApiExtension.communityApi.verifyGetOffender()
      assertThat(response.hits.asList()).extracting<String> { it.id }.containsExactly("X123456")
    }
  }

  @Nested
  inner class NoOffenderIndexes {
    @BeforeEach
    fun initialise() {
      deleteOffenderIndexes()
      initialiseIndexStatus()
    }

    @Test
    fun `Indexes absent, no update`() {
      val indexServiceLogAppender = findLogAppender(IndexService::class.java)
      CommunityApiExtension.communityApi.stubGetOffender()

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

      eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { indexServiceLogAppender.list } matches { it hasLogMessageContaining "Ignoring update of offender" }

      assertThatThrownBy { searchByCrn("X123456") }
          .isInstanceOf(ElasticsearchStatusException::class.java)
          .hasMessageContaining("no such index")
      CommunityApiExtension.communityApi.verifyNotGetOffender("X123456")
    }
  }

}