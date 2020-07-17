package uk.gov.justice.digital.hmpps.indexer.listeners

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.integration.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.indexer.integration.readResourceAsText

internal class IndexListenerIntegrationTest : QueueIntegrationTest() {
  @Test
  fun `will consume a index rebuild message`() {
    val message = "/messages/populateIndex.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }

    indexAwsSqsClient.sendMessage(indexQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
  }

}
