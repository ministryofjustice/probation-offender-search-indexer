package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.config.TelemetryEvents

@Service
class QueueAdminService(private val indexAwsSqsClient: AmazonSQS,
                        private val indexAwsSqsDlqClient: AmazonSQS,
                        private val eventAwsSqsClient: AmazonSQS,
                        private val eventAwsSqsDlqClient: AmazonSQS,
                        private val indexQueueService: IndexQueueService,
                        private val telemetryClient: TelemetryClient,
                        @Value("\${index.sqs.queue.name}") private val indexQueueName: String,
                        @Value("\${index.sqs.dlq.name}") private val indexDlqName: String,
                        @Value("\${event.sqs.queue.name}") private val eventQueueName: String,
                        @Value("\${event.sqs.dlq.name}") private val eventDlqName: String,
                        private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val indexQueueUrl: String by lazy { indexAwsSqsClient.getQueueUrl(indexQueueName).queueUrl }
  val indexDlqUrl: String by lazy { indexAwsSqsDlqClient.getQueueUrl(indexDlqName).queueUrl }
  val eventQueueUrl: String by lazy { eventAwsSqsClient.getQueueUrl(eventQueueName).queueUrl }
  val eventDlqUrl: String by lazy { eventAwsSqsDlqClient.getQueueUrl(eventDlqName).queueUrl }

  fun clearAllIndexQueueMessages() {
    val count = indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue()
    indexAwsSqsClient.purgeQueue(PurgeQueueRequest(indexQueueUrl))
    log.info("Clear all messages on index queue")
    telemetryClient.trackEvent(TelemetryEvents.PURGED_INDEX_QUEUE.name, mapOf("messages-on-queue" to count.toString()), null)
  }

  fun clearAllDlqMessagesForIndex() {
    val count = indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ()
    indexAwsSqsDlqClient.purgeQueue(PurgeQueueRequest(indexDlqUrl))
    log.info("Clear all messages on index dead letter queue")
    telemetryClient.trackEvent(TelemetryEvents.PURGED_INDEX_DLQ.name, mapOf("messages-on-queue" to count.toString()), null)
  }

  fun clearAllDlqMessagesForEvent() {
    eventAwsSqsDlqClient.purgeQueue(PurgeQueueRequest(eventDlqUrl))
    log.info("Clear all messages on event dead letter queue")
  }

  fun transferEventMessages() =
      repeat(getEventDlqMessageCount()) {
        eventAwsSqsDlqClient.receiveMessage(ReceiveMessageRequest(eventDlqUrl).withMaxNumberOfMessages(1)).messages
            .forEach { msg ->
              eventAwsSqsClient.sendMessage(eventQueueUrl, msg.body)
              eventAwsSqsDlqClient.deleteMessage(DeleteMessageRequest(eventDlqUrl, msg.receiptHandle))
            }
      }

  private fun getEventDlqMessageCount() =
      eventAwsSqsDlqClient.getQueueAttributes(eventDlqUrl, listOf("ApproximateNumberOfMessages"))
          .attributes["ApproximateNumberOfMessages"]
          ?.toInt() ?: 0

  fun transferIndexMessages() =
      indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ()
          .also { total ->
            repeat(total) {
              indexAwsSqsDlqClient.receiveMessage(ReceiveMessageRequest(indexDlqUrl).withMaxNumberOfMessages(1)).messages
                  .forEach { msg ->
                    indexAwsSqsClient.sendMessage(indexQueueUrl, msg.body)
                    indexAwsSqsDlqClient.deleteMessage(DeleteMessageRequest(indexDlqUrl, msg.receiptHandle))
                  }
            }
          }.let { total ->
            telemetryClient.trackEvent(TelemetryEvents.TRANSFERRED_INDEX_DLQ.name, mapOf("messages-on-queue" to total.toString()), null)
          }

}
