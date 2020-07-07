package uk.gov.justice.digital.hmpps.indexer.listeners

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.integration.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.indexer.integration.readResourceAsText

internal class EventListenerIntegrationTest : QueueIntegrationTest() {
  @Test
  fun `will consume a offender change event message`() {
    val message = "/messages/offenderChanged.json".readResourceAsText()

    // wait until our queue has been purged
    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }

    eventAwsSqsClient.sendMessage(eventQueueUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
  }

}

