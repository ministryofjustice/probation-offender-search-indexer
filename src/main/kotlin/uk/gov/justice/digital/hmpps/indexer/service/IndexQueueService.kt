package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexMessageRequest
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_OFFENDER_PAGE
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_OFFENDER
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class IndexQueueService(@Qualifier("indexAwsSqsClient") private val indexClient: AmazonSQS,
                        @Qualifier("eventAwsSqsClient") private val eventClient: AmazonSQS,
                        @Value("\${index.sqs.queue.name}") private val indexQueueName: String,
                        @Value("\${index.sqs.dlq.name}") private val indexDlqName: String,
                        @Value("\${event.sqs.dlq.name}") private val eventDlqName: String,
                        private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val queueUrl: String = indexClient.getQueueUrl(indexQueueName).queueUrl
  val indexDlqUrl: String = indexClient.getQueueUrl(indexDlqName).queueUrl
  val eventDlqUrl: String = eventClient.getQueueUrl(eventDlqName).queueUrl

  fun sendPopulateIndexMessage(index: SyncIndex) {
    val result = indexClient.sendMessage(SendMessageRequest(queueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_INDEX, index = index))))
    log.info("Sent populate index message request {}", result.messageId)
  }

  fun clearAllMessages()  {
    indexClient.purgeQueue(PurgeQueueRequest(queueUrl))
    log.info("Clear all messages on index queue")
  }

  fun sendPopulateOffenderPageMessage(offenderPage: OffenderPage) {
    val result = indexClient.sendMessage(SendMessageRequest(queueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_OFFENDER_PAGE, offenderPage = offenderPage))))
    log.info("Sent populate offender page message request {}", result.messageId)
  }

  fun sendPopulateOffenderMessage(crn: String) {
    val result = indexClient.sendMessage(SendMessageRequest(queueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_OFFENDER, crn = crn))))
    log.info("Sent populate offender message request {}", result.messageId)
  }

  fun clearAllDlqMessagesForIndex() {
    indexClient.purgeQueue(PurgeQueueRequest(indexDlqUrl))
    log.info("Clear all messages on index dead letter queue")
  }

  fun clearAllDlqMessagesForEvent() {
    eventClient.purgeQueue(PurgeQueueRequest(eventDlqUrl))
    log.info("Clear all messages on event dead letter queue")
  }
}
