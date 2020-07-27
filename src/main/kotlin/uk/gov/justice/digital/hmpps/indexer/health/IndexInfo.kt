package uk.gov.justice.digital.hmpps.indexer.health


import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.IndexStatusService
import java.util.NoSuchElementException


@Component
class IndexInfo(
    private val indexStatusService: IndexStatusService,
    private val indexService: IndexService,
    private val offenderRepository: OffenderRepository,
    @Value("\${index.sqs.queue.name}") private val indexQueueName: String,
    private val indexAwsSqsClient: AmazonSQS
) : InfoContributor {

  override fun contribute(builder: Info.Builder) {
    try {
      builder.withDetail("index-status", indexStatusService.getIndexStatus())
    } catch (e: NoSuchElementException) {
      builder.withDetail("index-status", "No status exists yet")
    }
    builder.withDetail("index-size", mapOf(
        GREEN to indexService.getIndexCount(GREEN),
        BLUE to indexService.getIndexCount(BLUE)
    ))
    builder.withDetail("offender-alias", offenderRepository.offenderAliasIsPointingAt().joinToString())
    builder.withDetail("index-queue-backlog", safeQueueCount())
  }

  private fun safeQueueCount(): String {
    return try {
      val url = indexAwsSqsClient.getQueueUrl(indexQueueName)
      val queueAttributes = indexAwsSqsClient.getQueueAttributes(GetQueueAttributesRequest(url.queueUrl).withAttributeNames(ApproximateNumberOfMessages))
          .attributes

      queueAttributes["ApproximateNumberOfMessages"] ?: "unknown"
    } catch (ex: Exception) {
      "error retrieving queue count: ${ex.message}"
    }
  }
}
