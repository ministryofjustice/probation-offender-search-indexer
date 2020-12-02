package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.services.sqs.AmazonSQS
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

data class IndexQueueStatus(val messagesOnQueue: Int, val messagesOnDlq: Int, val messagesInFlight: Int) {
  val active
    get() = messagesOnQueue > 0 || messagesOnDlq > 0 || messagesInFlight > 0
}

@Service
class IndexQueueService(
  private val indexAwsSqsClient: AmazonSQS,
  private val indexAwsSqsDlqClient: AmazonSQS,
  @Value("\${index.sqs.queue.name}") private val indexQueueName: String,
  @Value("\${index.sqs.dlq.name}") private val indexDlqName: String,
  private val gson: Gson
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val indexQueueUrl: String by lazy { indexAwsSqsClient.getQueueUrl(indexQueueName).queueUrl }
  val indexDlqUrl: String by lazy { indexAwsSqsDlqClient.getQueueUrl(indexDlqName).queueUrl }

  fun sendPopulateIndexMessage(index: SyncIndex) {
    val result = indexAwsSqsClient.sendMessage(SendMessageRequest(indexQueueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_INDEX, index = index))))
    log.info("Sent populate index message request {}", result.messageId)
  }

  fun sendPopulateOffenderPageMessage(offenderPage: OffenderPage) {
    val result = indexAwsSqsClient.sendMessage(SendMessageRequest(indexQueueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_OFFENDER_PAGE, offenderPage = offenderPage))))
    log.info("Sent populate offender page message request {} for page {}", result.messageId, offenderPage)
  }

  fun sendPopulateOffenderMessage(crn: String) {
    indexAwsSqsClient.sendMessage(SendMessageRequest(indexQueueUrl, gson.toJson(IndexMessageRequest(type = POPULATE_OFFENDER, crn = crn))))
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int {
    val queueAttributes = indexAwsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
  }

  fun getNumberOfMessagesCurrentlyOnIndexDLQ(): Int {
    val queueAttributes = indexAwsSqsDlqClient.getQueueAttributes(indexDlqUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
  }

  fun getNumberOfMessagesCurrentlyInFlight(): Int {
    val queueAttributes = indexAwsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessagesNotVisible"))
    return queueAttributes.attributes["ApproximateNumberOfMessagesNotVisible"]?.toInt() ?: 0
  }

  fun getIndexQueueStatus(): IndexQueueStatus =
    IndexQueueStatus(
      messagesOnQueue = getNumberOfMessagesCurrentlyOnIndexQueue(),
      messagesInFlight = getNumberOfMessagesCurrentlyInFlight(),
      messagesOnDlq = getNumberOfMessagesCurrentlyOnIndexDLQ()
    )
}
