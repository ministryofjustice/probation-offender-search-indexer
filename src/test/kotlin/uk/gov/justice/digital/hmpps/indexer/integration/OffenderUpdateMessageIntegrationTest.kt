package uk.gov.justice.digital.hmpps.indexer.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

class OffenderUpdateMessageIntegrationTest : QueueIntegrationTest() {

  @BeforeEach
  fun initialise() {
    tearDownIndexes()
    setupIndexes()
  }

  @Test
  fun `Receive offender update, search index is updated`() {
    CommunityApiExtension.communityApi.stubGetOffender()

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventAwsSqsClient.sendMessage(eventQueueUrl, "/messages/offenderChanged.json".readResourceAsText())

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
    await untilCallTo { CommunityApiExtension.communityApi.getCountFor("/secure/offenders/crn/X123456/all") } matches { it == 1 }
    await untilCallTo { indexService.getIndexCount(SyncIndex.GREEN) } matches { it == 1L }

    val response = search("X123456")

    CommunityApiExtension.communityApi.verifyGetOffender()
    assertThat(response.hits.hits.asList()).extracting<String> { it.id }.containsExactly("X123456")
  }
}