package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class IndexService(
    private val indexStatusService: IndexStatusService,
    private val offenderSynchroniserService: OffenderSynchroniserService,
    private val indexQueueService: IndexQueueService
) {

  companion object : Logging

  fun prepareIndexForRebuild(): Either<BuildIndexError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState == IndexState.BUILDING) {
      return BuildIndexError.BuildAlreadyInProgress(indexStatus).left()
    }
    // TODO DT-961 log index status e.g. "Current index is {} [{}], rebuilding index {} [{}]"
    indexStatusService.markBuildInProgress()
    offenderSynchroniserService.checkExistsAndReset(indexStatus.otherIndex)
    indexQueueService.sendPopulateIndexMessage(indexStatus.otherIndex)

    return indexStatusService.getIndexStatus().right()
  }
  fun markIndexingComplete(): Either<MarkBuildCompleteError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return MarkBuildCompleteError.BuildNotInProgress(indexStatus).left()
    }

    indexStatusService.markBuildCompleteAndSwitchIndex()
    indexQueueService.clearAllMessages()
    logger.info {"Index ${indexStatus.otherIndex} marked as ${indexStatus.otherIndexState}, ${indexStatus.currentIndex} is now current"}

    return indexStatusService.getIndexStatus().right()
  }

  fun cancelIndexing(): Either<CancelBuildIndexError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return CancelBuildIndexError.BuildNotInProgress(indexStatus).left()
    }

    indexStatusService.markBuildCancelled()
    logger.info {"Index ${indexStatus.currentIndex.otherIndex()} marked as ${indexStatus.otherIndexState}, ${indexStatus.currentIndex} is still current"}

    return indexStatusService.getIndexStatus().right()
  }

  fun indexOffender(crn: String) = offenderSynchroniserService.synchroniseOffender(crn)
  fun populateIndex(index: SyncIndex) = log.error("Not implemented yet")
}


sealed class BuildIndexError(val message: String) {
  data class BuildAlreadyInProgress(val indexStatus: IndexStatus): BuildIndexError("The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})")
}

sealed class MarkBuildCompleteError(val message: String) {
  data class BuildNotInProgress(val indexStatus: IndexStatus) : MarkBuildCompleteError("The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})")
}

sealed class CancelBuildIndexError(val message: String) {
  data class BuildNotInProgress(val indexStatus: IndexStatus): CancelBuildIndexError("The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})")
}