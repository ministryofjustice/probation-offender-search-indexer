package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexMessageRequest
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_OFFENDER
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_OFFENDER_PAGE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class IndexQueueService(private val indexAwsSqsClient: AmazonSQS,
                        private val indexAwsSqsDlqClient: AmazonSQS,
                        private val eventAwsSqsClient: AmazonSQS,
                        private val eventAwsSqsDlqClient: AmazonSQS,
                        @Value("\${index.sqs.queue.name}") private val indexQueueName: String,
                        @Value("\${index.sqs.dlq.name}") private val indexDlqName: String,
                        @Value("\${event.sqs.queue.name}") private val eventQueueName: String,
                        @Value("\${event.sqs.dlq.name}") private val eventDlqName: String,
                        private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val indexQueueUrl: String = indexAwsSqsClient.getQueueUrl(indexQueueName).queueUrl
  val indexDlqUrl: String = indexAwsSqsDlqClient.getQueueUrl(indexDlqName).queueUrl
  val eventQueueUrl: String = eventAwsSqsClient.getQueueUrl(eventQueueName).queueUrl
  val eventDlqUrl: String = eventAwsSqsDlqClient.getQueueUrl(eventDlqName).queueUrl

  fun sendPopulateIndexMessage(index: SyncIndex) {
    val result = indexAwsSqsClient.sendMessage(SendMessageRequest(indexQueueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_INDEX, index = index))))
    log.info("Sent populate index message request {}", result.messageId)
  }

  fun clearAllMessages() {
    indexAwsSqsClient.purgeQueue(PurgeQueueRequest(indexQueueUrl))
    log.info("Clear all messages on index queue")
  }

  fun sendPopulateOffenderPageMessage(offenderPage: OffenderPage) {
    val result = indexAwsSqsClient.sendMessage(SendMessageRequest(indexQueueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_OFFENDER_PAGE, offenderPage = offenderPage))))
    log.info("Sent populate offender page message request {}", result.messageId)
  }

  fun sendPopulateOffenderMessage(crn: String) {
    val result = indexAwsSqsClient.sendMessage(SendMessageRequest(indexQueueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_OFFENDER, crn = crn))))
    log.info("Sent populate offender message request {}", result.messageId)
  }

  fun clearAllDlqMessagesForIndex() {
    indexAwsSqsDlqClient.purgeQueue(PurgeQueueRequest(indexDlqUrl))
    log.info("Clear all messages on index dead letter queue")
  }

  fun clearAllDlqMessagesForEvent() {
    eventAwsSqsDlqClient.purgeQueue(PurgeQueueRequest(eventDlqUrl))
    log.info("Clear all messages on event dead letter queue")
  }

  fun transferEventMessages() =
      repeat(getEventDlqMessageCount()) {
        eventAwsSqsDlqClient.receiveMessage(ReceiveMessageRequest(eventDlqUrl).withMaxNumberOfMessages(1)).messages
            .forEach { eventAwsSqsClient.sendMessage(eventQueueUrl, it.body) }
      }

  private fun getEventDlqMessageCount() =
      eventAwsSqsDlqClient.getQueueAttributes(eventDlqUrl, listOf("ApproximateNumberOfMessages"))
          .attributes["ApproximateNumberOfMessages"]
          ?.toInt() ?: 0

  fun transferIndexMessages() =
      repeat(getIndexDlqMessageCount()) {
        indexAwsSqsDlqClient.receiveMessage(ReceiveMessageRequest(indexDlqUrl).withMaxNumberOfMessages(1)).messages
            .forEach { indexAwsSqsClient.sendMessage(indexQueueUrl, it.body) }
      }

  private fun getIndexDlqMessageCount() =
      indexAwsSqsDlqClient.getQueueAttributes(indexDlqUrl, listOf("ApproximateNumberOfMessages"))
          .attributes["ApproximateNumberOfMessages"]
          ?.toInt() ?: 0

}
