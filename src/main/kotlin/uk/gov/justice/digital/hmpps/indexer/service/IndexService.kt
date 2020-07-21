package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.slf4j.Logger
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
    private val elasticSearchClient: RestHighLevelClient
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun prepareIndexForRebuild(): Either<BuildIndexError, IndexStatus> {
    val indexStatus = indexStatusService.initialiseIndexWhenRequired().getIndexStatus()
    if (indexStatus.otherIndexState == IndexState.BUILDING) {
      return BuildIndexError.BuildAlreadyInProgress(indexStatus).left()
    }
    logIndexStatuses(indexStatus)
    indexStatusService.markBuildInProgress()
    offenderSynchroniserService.checkExistsAndReset(indexStatus.otherIndex)
    indexQueueService.sendPopulateIndexMessage(indexStatus.otherIndex)

    return indexStatusService.getIndexStatus().right()
  }

  private fun logIndexStatuses(indexStatus: IndexStatus) {
    log.info("Current index is {} with state {} [{}], other index is {} with state {} [{}]",
        indexStatus.currentIndex.indexName, indexStatus.currentIndexState, getIndexCount(indexStatus.currentIndex),
        indexStatus.otherIndex.indexName, indexStatus.otherIndexState, getIndexCount(indexStatus.otherIndex)
    )
  }

  fun markIndexingComplete(): Either<MarkBuildCompleteError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return MarkBuildCompleteError.BuildNotInProgress(indexStatus).left()
    }

    indexStatusService.markBuildCompleteAndSwitchIndex()
    indexQueueService.clearAllMessages()
    log.info("Index ${indexStatus.otherIndex} marked as ${indexStatus.otherIndexState}, ${indexStatus.currentIndex} is now current")

    return indexStatusService.getIndexStatus().right()
  }

  fun cancelIndexing(): Either<CancelBuildIndexError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return CancelBuildIndexError.BuildNotInProgress(indexStatus).left()
    }

    indexStatusService.markBuildCancelled()
    log.info("Index ${indexStatus.currentIndex.otherIndex()} marked as ${indexStatus.otherIndexState}, ${indexStatus.currentIndex} is still current")

    return indexStatusService.getIndexStatus().right()
  }

  fun indexOffender(crn: String) : String {
    val indexStatus = indexStatusService.getIndexStatus()
    // TODO - this needs to go to both indexes (unless one is new maybe?)
    return offenderSynchroniserService.synchroniseOffender(crn, indexStatus.currentIndex)
  }
  fun populateIndex(index: SyncIndex): Either<PopulateIndexError, Int> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return PopulateIndexError.BuildNotInProgress(indexStatus).left()
    }

    if (indexStatus.currentIndex.otherIndex() != index) {
      return PopulateIndexError.WrongIndexRequested(indexStatus).left()
    }

    val chunks = offenderSynchroniserService.splitAllOffendersIntoChunks()
    chunks.forEach { indexQueueService.sendPopulateOffenderPageMessage(it) }
    return chunks.size.right()
  }

  fun populateIndexWithOffenderPage(offenderPage: OffenderPage) {
    offenderSynchroniserService.getAllOffenderIdentifiersInPage(offenderPage)
        .forEach { indexQueueService.sendPopulateOffenderMessage(it.crn) }
  }

  fun populateIndexWithOffender(crn: String) : Either<PopulateIndexError, String> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return PopulateIndexError.BuildNotInProgress(indexStatus).left()
    }

    return offenderSynchroniserService.synchroniseOffender(crn, indexStatus.currentIndex.otherIndex()).right()
  }

  fun getIndexCount(index: SyncIndex): Long {
    val request = CountRequest(index.indexName)
    return try { elasticSearchClient.count(request, RequestOptions.DEFAULT).count } catch (e: Exception) { -1L }
  }

}


sealed class BuildIndexError(val message: String) {
  data class BuildAlreadyInProgress(val indexStatus: IndexStatus) : BuildIndexError("The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})")
}

sealed class MarkBuildCompleteError(val message: String) {
  data class BuildNotInProgress(val indexStatus: IndexStatus) : MarkBuildCompleteError("The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})")
}

sealed class CancelBuildIndexError(val message: String) {
  data class BuildNotInProgress(val indexStatus: IndexStatus) : CancelBuildIndexError("The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})")
}

sealed class PopulateIndexError(val message: String) {
  data class BuildNotInProgress(val indexStatus: IndexStatus) : PopulateIndexError("The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})")
  data class WrongIndexRequested(val indexStatus: IndexStatus) : PopulateIndexError("The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})")
}
