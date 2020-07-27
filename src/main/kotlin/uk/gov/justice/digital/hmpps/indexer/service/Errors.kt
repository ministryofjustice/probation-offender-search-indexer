package uk.gov.justice.digital.hmpps.indexer.service

import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus


interface Error {
  fun message(): String
}

interface IndexError: Error
interface OffenderError: Error

data class BuildAlreadyInProgress(val indexStatus: IndexStatus): IndexError {
  override fun message() = "The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
}

data class BuildNotInProgress(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class WrongIndexRequested(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class NoActiveIndexes(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "Cannot update current index ${indexStatus.currentIndex} which is in state ${indexStatus.currentIndexState} and other index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState}"
}

data class OffenderNotFound(val crn: String): OffenderError {
  override fun message() = "The offender $crn could not be found"
}
