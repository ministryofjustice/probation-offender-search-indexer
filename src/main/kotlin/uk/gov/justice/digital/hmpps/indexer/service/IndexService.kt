package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.amazonaws.util.IOUtils
import com.google.gson.JsonParser
import org.elasticsearch.client.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class IndexService(
    private val indexStatusService: IndexStatusService,
    private val offenderSynchroniserService: OffenderSynchroniserService,
    private val indexQueueService: IndexQueueService,
    private val searchClient: SearchClient
) {

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun buildIndex(): Either<BuildIndexError, IndexStatus> {
    val indexStatus = indexStatusService.getOrCreateCurrentIndexStatus()
    if (indexStatus.state == IndexState.BUILDING) {
      return BuildIndexError.BuildAlreadyInProgress(indexStatus).left()
    }
    log.info(allIndexesSummary(indexStatus.currentIndex))
    indexStatusService.markBuildInProgress()
    offenderSynchroniserService.checkExistsAndReset(indexStatus.currentIndex.otherIndex())
    indexQueueService.sendIndexRequestMessage()

    return indexStatusService.getOrCreateCurrentIndexStatus().right()
  }

  private fun countIndex(syncIndex: SyncIndex): Int {
    val response = searchClient.lowLevelClient().performRequest(Request("get", "/${syncIndex.indexName}/_count"))
    return JsonParser.parseString(IOUtils.toString(response.entity.content)).asJsonObject["count"].asInt
  }

  private fun allIndexesSummary(currentIndex: SyncIndex) =
      "Current index is $currentIndex [${countIndex(currentIndex)}], rebuilding ${currentIndex.otherIndex()} [${countIndex(currentIndex.otherIndex())}]}"

  fun markIndexingComplete() = log.info("Received request to mark indexing complete")

  fun cancelIndexing() = log.info("Received request to cancel indexing")

  fun indexOffender(crn: String) = offenderSynchroniserService.synchroniseOffender(crn)
}


@Suppress("UNUSED_PARAMETER")
sealed class BuildIndexError(val message: String) {
  data class BuildAlreadyInProgress(val indexStatus: IndexStatus): BuildIndexError("The build for ${indexStatus.currentIndex} is already ${indexStatus.state} (started at ${indexStatus.startIndexTime})")
}