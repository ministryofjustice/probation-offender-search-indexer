package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus

@Service
class IndexService(
    private val indexStatusService: IndexStatusService,
    private val offenderSynchroniserService: OffenderSynchroniserService,
    private val indexQueueService: IndexQueueService
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun buildIndex(): Either<BuildIndexError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState == IndexState.BUILDING) {
      return BuildIndexError.BuildAlreadyInProgress(indexStatus).left()
    }
    // TODO DT-961 log index status e.g. "Current index is {} [{}], rebuilding index {} [{}]"
    indexStatusService.markBuildInProgress()
    offenderSynchroniserService.checkExistsAndReset(indexStatus.otherIndex)
    indexQueueService.sendIndexRequestMessage()

    return indexStatusService.getIndexStatus().right()
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

  fun indexOffender(crn: String) = offenderSynchroniserService.synchroniseOffender(crn)
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