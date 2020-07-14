package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun buildIndex(): Either<BuildIndexError, IndexStatus> {
    val indexStatus = indexStatusService.getOrCreateCurrentIndexStatus()
    if (indexStatus.state == IndexState.BUILDING) {
      return BuildIndexError.BuildAlreadyInProgress(indexStatus).left()
    }

    // TODO DT-961 log out the status of both indexes and which one is currently building

    offenderSynchroniserService.checkExistsAndReset(indexStatus.currentIndex.otherIndex())
    indexQueueService.sendIndexRequestMessage()

    return indexStatusService.getOrCreateCurrentIndexStatus().right()
  }

  fun markIndexingComplete() = log.info("Received request to mark indexing complete")

  fun cancelIndexing() = log.info("Received request to cancel indexing")

  fun indexOffender(crn: String) = offenderSynchroniserService.synchroniseOffender(crn)
}


@Suppress("UNUSED_PARAMETER")
sealed class BuildIndexError(errorMessage: String, open val indexStatus: IndexStatus) {
  data class BuildAlreadyInProgress(override val indexStatus: IndexStatus): BuildIndexError("The build is already in progress", indexStatus)
}