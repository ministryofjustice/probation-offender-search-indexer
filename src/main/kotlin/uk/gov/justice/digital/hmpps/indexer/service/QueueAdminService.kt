package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class QueueAdminService(private val indexAwsSqsClient: AmazonSQS,
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

  fun clearAllIndexQueueMessages() {
    indexAwsSqsClient.purgeQueue(PurgeQueueRequest(indexQueueUrl))
    log.info("Clear all messages on index queue")
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
