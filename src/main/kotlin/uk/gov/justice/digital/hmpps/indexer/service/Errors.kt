package uk.gov.justice.digital.hmpps.indexer.service

import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

interface Error {
  fun message(): String
}

interface IndexError : Error
interface OffenderError : Error

data class BuildAlreadyInProgressError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
}

data class BuildNotInProgressError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class WrongIndexRequestedError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class NoActiveIndexesError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "Cannot update current index ${indexStatus.currentIndex} which is in state ${indexStatus.currentIndexState} and other index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState}"
}

data class OffenderNotFoundError(val crn: String) : OffenderError {
  override fun message() = "The offender $crn could not be found"
}

data class ActiveMessagesExistError(val index: SyncIndex, val indexQueueStatus: IndexQueueStatus, val action: String) : IndexError {
  override fun message() = "The index ${index.indexName} has active messages $indexQueueStatus so we cannot process $action"
}
