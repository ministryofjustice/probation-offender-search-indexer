package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
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

  fun prepareIndexForRebuild(): Either<IndexError, IndexStatus> {
    val indexStatus = indexStatusService.initialiseIndexWhenRequired().getIndexStatus()
    if (indexStatus.otherIndexState == IndexState.BUILDING) {
      return BuildAlreadyInProgress(indexStatus).left()
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

  fun markIndexingComplete(): Either<IndexError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return BuildNotInProgress(indexStatus).left()
    }

    val newIndexStatus = indexStatusService.markBuildCompleteAndSwitchIndex()
    offenderSynchroniserService.switchAliasIndex(newIndexStatus.currentIndex)

    indexQueueService.clearAllMessages()
    log.info("Index ${newIndexStatus.otherIndex} marked as ${newIndexStatus.otherIndexState}, ${newIndexStatus.currentIndex} is now current")

    return indexStatusService.getIndexStatus().right()
  }

  fun cancelIndexing(): Either<IndexError, IndexStatus> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return BuildNotInProgress(indexStatus).left()
    }

    indexStatusService.markBuildCancelled()
    indexQueueService.clearAllMessages()
    log.info("Index ${indexStatus.currentIndex.otherIndex()} marked as ${indexStatus.otherIndexState}, ${indexStatus.currentIndex} is still current")

    return indexStatusService.getIndexStatus().right()
  }

  fun updateOffender(crn: String) : Either<Error, String> {
    val indexStatus = indexStatusService.getIndexStatus()
    val activeIndexes = indexStatus.activeIndexes()
    if (activeIndexes.isEmpty()) {
      log.info("Ignoring update of offender {} as no indexes were active", crn)
      return NoActiveIndexes(indexStatus).left()
    }
    log.info("Updating offender {} on indexes {}", crn, activeIndexes)
    return offenderSynchroniserService.synchroniseOffender(crn, *activeIndexes.toTypedArray())
        .flatMap { it.right() }
  }

  fun populateIndex(index: SyncIndex): Either<Error, Int> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return BuildNotInProgress(indexStatus).left()
    }

    if (indexStatus.currentIndex.otherIndex() != index) {
      return WrongIndexRequested(indexStatus).left()
    }

    val chunks = offenderSynchroniserService.splitAllOffendersIntoChunks()
    chunks.forEach { indexQueueService.sendPopulateOffenderPageMessage(it) }
    return chunks.size.right()
  }

  fun populateIndexWithOffenderPage(offenderPage: OffenderPage) {
    offenderSynchroniserService.getAllOffenderIdentifiersInPage(offenderPage)
        .forEach { indexQueueService.sendPopulateOffenderMessage(it.crn) }
  }

  fun populateIndexWithOffender(crn: String) : Either<Error, String> {
    val indexStatus = indexStatusService.getIndexStatus()
    if (indexStatus.otherIndexState != IndexState.BUILDING) {
      return BuildNotInProgress(indexStatus).left()
    }

    return offenderSynchroniserService.synchroniseOffender(crn, indexStatus.currentIndex.otherIndex())
        .map { it.right() }
        .getOrHandle { OffenderNotFound(crn).left() }
  }

  fun getIndexCount(index: SyncIndex): Long {
    val request = CountRequest(index.indexName)
    return try { elasticSearchClient.count(request, RequestOptions.DEFAULT).count } catch (e: Exception) { -1L }
  }

}
