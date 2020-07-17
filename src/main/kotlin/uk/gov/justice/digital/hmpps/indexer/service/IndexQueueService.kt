package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexMessageRequest
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class IndexQueueService(@Qualifier("indexAwsSqsClient") private val client: AmazonSQS,
                        @Value("\${index.sqs.queue.name}") private val indexQueueName: String,
                        private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val queueUrl = client.getQueueUrl(indexQueueName).queueUrl

  fun sendPopulateIndexMessage(index: SyncIndex) {
    val result = client.sendMessage(SendMessageRequest(queueUrl, gson.toJson(IndexMessageRequest(POPULATE_INDEX, index))))
    log.info("Sent rebuild index message request {}", result.messageId)
  }

  fun clearAllMessages() = null
}

