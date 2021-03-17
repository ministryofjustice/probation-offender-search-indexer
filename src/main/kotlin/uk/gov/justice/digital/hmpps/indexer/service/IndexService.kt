package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import kotlin.reflect.KClass

@Service
class IndexService(
  private val indexStatusService: IndexStatusService,
  private val offenderSynchroniserService: OffenderSynchroniserService,
  private val indexQueueService: IndexQueueService,
  private val queueAdminService: QueueAdminService,
  private val elasticSearchClient: RestHighLevelClient,
  private val telemetryClient: TelemetryClient,
  private val indexBuildProperties: IndexBuildProperties,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun prepareIndexForRebuild(): Either<Error, IndexStatus> {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    return indexStatusService.initialiseIndexWhenRequired().getIndexStatus()
      .also { logIndexStatuses(it) }
      .failIf(IndexStatus::isBuilding) { BuildAlreadyInProgressError(it) }
      .failIf({ indexQueueStatus.active }) { ActiveMessagesExistError(it.otherIndex, indexQueueStatus, "build index") }
      .map { doPrepareIndexForRebuild(it) }
  }

  private fun doPrepareIndexForRebuild(indexStatus: IndexStatus): IndexStatus {
    indexStatusService.markBuildInProgress()
    offenderSynchroniserService.checkExistsAndReset(indexStatus.otherIndex)
    indexQueueService.sendPopulateIndexMessage(indexStatus.otherIndex)
    return indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .also { telemetryClient.trackEvent(TelemetryEvents.BUILDING_INDEX.name, mapOf("index" to indexStatus.otherIndex.name), null) }
  }

  private fun logIndexStatuses(indexStatus: IndexStatus) {
    log.info(
      "Current index status is {}.  Index counts {}={} and {}={}.  Queue counts: Queue={} and DLQ={}",
      indexStatus,
      indexStatus.currentIndex,
      getIndexCount(indexStatus.currentIndex),
      indexStatus.otherIndex,
      getIndexCount(indexStatus.otherIndex),
      indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue(),
      indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ()
    )
  }

  fun markIndexingComplete(ignoreThreshold: Boolean): Either<Error, IndexStatus> {
    val indexQueueStatus = indexQueueService.getIndexQueueStatus()
    val indexStatus = indexStatusService.getIndexStatus()
    return indexStatus
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
      .failIf({ indexQueueStatus.active }) { ActiveMessagesExistError(it.otherIndex, indexQueueStatus, "mark complete") }
      .failIf({ ignoreThreshold.not() && indexSizeNotReachedThreshold(it) }) { ThresholdNotReachedError(it.otherIndex, indexBuildProperties.completeThreshold) }
      .also { logIndexStatuses(indexStatus) }
      .map { doMarkIndexingComplete() }
  }

  private fun indexSizeNotReachedThreshold(indexStatus: IndexStatus): Boolean =
    getIndexCount(indexStatus.currentIndex) < indexBuildProperties.completeThreshold

  private fun doMarkIndexingComplete(): IndexStatus =
    indexStatusService.markBuildCompleteAndSwitchIndex()
      .let { newStatus ->
        offenderSynchroniserService.switchAliasIndex(newStatus.currentIndex)
        return indexStatusService.getIndexStatus()
          .also { latestStatus -> logIndexStatuses(latestStatus) }
          .also { telemetryClient.trackEvent(TelemetryEvents.COMPLETED_BUILDING_INDEX.name, mapOf("index" to it.currentIndex.name), null) }
      }

  fun cancelIndexing(): Either<Error, IndexStatus> =
    indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
      .map { doCancelIndexing() }

  private fun doCancelIndexing(): IndexStatus {
    indexStatusService.markBuildCancelled()
    queueAdminService.clearAllIndexQueueMessages()
    return indexStatusService.getIndexStatus()
      .also { logIndexStatuses(it) }
      .also { telemetryClient.trackEvent(TelemetryEvents.CANCELLED_BUILDING_INDEX.name, mapOf("index" to it.otherIndex.name), null) }
  }

  fun updateOffender(crn: String): Either<Error, String> =
    indexStatusService.getIndexStatus()
      .failIf(IndexStatus::activeIndexesEmpty) {
        log.info("Ignoring update of offender {} as no indexes were active", crn)
        NoActiveIndexesError(it)
      }
      .flatMap { doUpdateOffender(it, crn) }

  private fun doUpdateOffender(indexStatus: IndexStatus, crn: String) =
    with(indexStatus.activeIndexes()) {
      log.info("Updating offender {} on indexes {}", crn, this)
      offenderSynchroniserService.synchroniseOffender(crn, *this.toTypedArray())
    }

  fun populateIndex(index: SyncIndex): Either<Error, Int> =
    executeAndTrackTimeMillis(TelemetryEvents.BUILD_INDEX_MSG.name) {
      indexStatusService.getIndexStatus()
        .also { logIndexStatuses(it) }
        .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
        .failIf({ it.currentIndex.otherIndex() != index }) { WrongIndexRequestedError(it) }
        .map { doPopulateIndex() }
    }

  private inline fun <R> executeAndTrackTimeMillis(trackEventName: String, properties: Map<String, String> = mapOf(), block: () -> R): R {
    val start = System.currentTimeMillis()
    val result = block()
    telemetryClient.trackEvent(
      trackEventName,
      mutableMapOf("messageTimeMs" to (System.currentTimeMillis() - start).toString()).plus(properties),
      null
    )
    return result
  }
  private fun doPopulateIndex(): Int {
    val chunks = offenderSynchroniserService.splitAllOffendersIntoChunks()
    chunks.forEach { indexQueueService.sendPopulateOffenderPageMessage(it) }
    return chunks.size
  }

  fun populateIndexWithOffenderPage(offenderPage: OffenderPage): Either<Error, Unit> =
    executeAndTrackTimeMillis(TelemetryEvents.BUILD_PAGE_MSG.name, mapOf("offenderPage" to offenderPage.page.toString())) {
      indexStatusService.getIndexStatus()
        .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
        .flatMap {
          offenderSynchroniserService.getAllOffenderIdentifiersInPage(offenderPage)
            .forEachIndexed { index, offenderIdentifier ->
              if (index == 0 || index.toLong() == offenderPage.pageSize - 1) {
                telemetryClient.trackEvent(TelemetryEvents.BUILD_PAGE_BOUNDARY.name, mutableMapOf("page" to offenderPage.page.toString(), "IndexOnPage" to index.toString(), "crn" to offenderIdentifier.crn), null)
              }
              indexQueueService.sendPopulateOffenderMessage(offenderIdentifier.crn)
            }.right()
        }
    }

  fun populateIndexWithOffender(crn: String): Either<Error, String> =
    indexStatusService.getIndexStatus()
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
      .flatMap { offenderSynchroniserService.synchroniseOffender(crn, it.currentIndex.otherIndex()) }

  fun getIndexCount(index: SyncIndex): Long {
    val request = CountRequest(index.indexName)
    return try {
      elasticSearchClient.count(request, RequestOptions.DEFAULT).count
    } catch (e: Exception) {
      -1L
    }
  }

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> Error
  ): Either<Error, IndexStatus> =
    when (check(this)) {
      false -> this.right()
      true -> onFail(this).left()
    }

  private inline fun Either<Error, IndexStatus>.failIf(
    crossinline check: (IndexStatus) -> Boolean,
    crossinline onFail: (IndexStatus) -> Error
  ): Either<Error, IndexStatus> =
    when (this.isLeft()) {
      true -> this
      false -> this.flatMap {
        it.failIf(check, onFail)
      }
    }
}

enum class UpdateOffenderError(val errorClass: KClass<out Error>) {
  NO_ACTIVE_INDEXES(NoActiveIndexesError::class),
  OFFENDER_NOT_FOUND(OffenderNotFoundError::class);

  companion object {
    fun fromErrorClass(error: Error): UpdateOffenderError {
      return values().first { it.errorClass == error::class }
    }
  }
}

enum class PrepareRebuildError(val errorClass: KClass<out Error>) {
  BUILD_IN_PROGRESS(BuildAlreadyInProgressError::class),
  ACTIVE_MESSAGES_EXIST(ActiveMessagesExistError::class);

  companion object {
    fun fromErrorClass(error: Error): PrepareRebuildError {
      return values().first { it.errorClass == error::class }
    }
  }
}

enum class MarkCompleteError(val errorClass: KClass<out Error>) {
  BUILD_NOT_IN_PROGRESS(BuildNotInProgressError::class),
  ACTIVE_MESSAGES_EXIST(ActiveMessagesExistError::class),
  THRESHOLD_NOT_REACHED(ThresholdNotReachedError::class);

  companion object {
    fun fromErrorClass(error: Error): MarkCompleteError {
      return values().first { it.errorClass == error::class }
    }
  }
}

enum class CancelBuildError(val errorClass: KClass<out Error>) {
  BUILD_NOT_IN_PROGRESS(BuildNotInProgressError::class);

  companion object {
    fun fromErrorClass(error: Error): CancelBuildError {
      return values().first { it.errorClass == error::class }
    }
  }
}
