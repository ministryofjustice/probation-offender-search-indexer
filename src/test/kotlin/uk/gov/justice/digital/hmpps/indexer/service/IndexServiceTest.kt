package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.ABSENT
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.NONE

class IndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val offenderSynchroniserService = mock<OffenderSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val queueAdminService = mock<QueueAdminService>()
  private val elasticSearchClient = mock<RestHighLevelClient>()
  private val telemetryClient = mock<TelemetryClient>()
  private val indexBuildProperties = mock<IndexBuildProperties>()
  private val indexService = IndexService(indexStatusService, offenderSynchroniserService, indexQueueService, queueAdminService, elasticSearchClient, telemetryClient, indexBuildProperties)

  @Nested
  inner class BuildIndex {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.initialiseIndexWhenRequired()).thenReturn(indexStatusService)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))
    }

    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildAlreadyInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index has active messages returns an error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft ActiveMessagesExistError(BLUE, expectedIndexQueueStatus, "build index")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(indexStatusService).markBuildInProgress()
    }

    @Test
    fun `A request is made to reset the other index`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(offenderSynchroniserService).checkExistsAndReset(BLUE)
    }

    @Test
    fun `A request is made to build other index`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(indexQueueService).sendPopulateIndexMessage(any())
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))

      indexService.prepareIndexForRebuild()

      verify(telemetryClient).trackEvent(TelemetryEvents.BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `The updated index is returned`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT))
        .thenReturn(expectedIndexStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService, times(2)).getIndexStatus()
      result shouldBeRight expectedIndexStatus
    }
  }

  @Nested
  inner class MarkIndexingComplete {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))
    }

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildNotInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index with active messages returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      val result = indexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft ActiveMessagesExistError(BLUE, expectedIndexQueueStatus, "mark complete")
    }

    @Test
    fun `Index not reached threshold returns error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

      val result = indexService.markIndexingComplete(ignoreThreshold = false)

      verify(indexStatusService).getIndexStatus()
      verify(indexQueueService).getIndexQueueStatus()
      result shouldBeLeft ThresholdNotReachedError(BLUE, 1000000)
    }

    @Test
    fun `A request is made to mark the index state as complete`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService).markBuildCompleteAndSwitchIndex()
    }

    @Test
    fun `Index not reached threshold but ignoring threshold - completes ok`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
      val expectedIndexQueueStatus = IndexQueueStatus(0, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)
      whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

      val result = indexService.markIndexingComplete(ignoreThreshold = false)

      verify(indexStatusService).getIndexStatus()
      verify(indexQueueService).getIndexQueueStatus()
      result shouldBeLeft ThresholdNotReachedError(BLUE, 1000000)
    }

    @Test
    fun `A request is made to switch alias`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.markIndexingComplete(ignoreThreshold = true)

      verify(offenderSynchroniserService).switchAliasIndex(BLUE)
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.markIndexingComplete(ignoreThreshold = true)

      verify(telemetryClient).trackEvent(TelemetryEvents.COMPLETED_BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Once current index marked as complete, the 'other' index is current`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      val result = indexService.markIndexingComplete(ignoreThreshold = true)

      verify(indexStatusService, times(2)).getIndexStatus()
      result shouldBeRight IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED)
    }
  }

  @Nested
  inner class SwitchIndex {

    @Test
    fun `A request is made to switch the indexes`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      val result = indexService.switchIndex(false)

      verify(indexStatusService).switchIndex()
      result shouldBeRight IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED)
    }

    @Test
    fun `A request is made to switch alias`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.switchIndex(false)

      verify(offenderSynchroniserService).switchAliasIndex(BLUE)
    }

    @Test
    fun `A telemetry event is sent`() {
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED))
        .thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))
      whenever(indexStatusService.switchIndex()).thenReturn(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED))

      indexService.switchIndex(false)

      verify(telemetryClient).trackEvent(TelemetryEvents.SWITCH_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Index building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildInProgressError(expectedIndexStatus)
    }

    @Test
    fun `Index Cancelled returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildCancelledError(expectedIndexStatus)
    }

    @Test
    fun `Index Absent returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.switchIndex(false)

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildAbsentError(expectedIndexStatus)
    }
  }

  @Nested
  inner class CancelIndexing {

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.cancelIndexing()

      verify(indexStatusService).getIndexStatus()
      result shouldBeLeft BuildNotInProgressError(expectedIndexStatus)
    }

    @Test
    fun `A request is made to mark the index state as cancelled`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(indexStatusService).markBuildCancelled()
    }

    @Test
    fun `all messages are cleared`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(queueAdminService).clearAllIndexQueueMessages()
    }

    @Test
    fun `A telemetry event is sent`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(telemetryClient).trackEvent(TelemetryEvents.CANCELLED_BUILDING_INDEX.name, mapOf("index" to "BLUE"), null)
    }

    @Test
    fun `Once current index marked as cancelled, the 'other' index is current`() {
      val expectedIndexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = CANCELLED)
      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING))
        .thenReturn(expectedIndexStatus)

      val result = indexService.cancelIndexing()

      verify(indexStatusService, times(2)).getIndexStatus()
      result shouldBeRight expectedIndexStatus
    }
  }

  @Nested
  inner class IndexOffender {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderSynchroniserService.synchroniseOffender(any(), any())).thenReturn("""{"offenderId": 99}""".right())
    }

    @Test
    internal fun `will delegate to synchronisation service`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      indexService.updateOffender("X12345")

      verify(offenderSynchroniserService).synchroniseOffender("X12345", GREEN)
    }
  }

  @Nested
  inner class PopulateIndex {

    @Test
    internal fun `will return an error if indexing is not in progress`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndex(GREEN)

      result shouldBeLeft BuildNotInProgressError(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing request is for the wrong index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)

      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndex(GREEN)

      result shouldBeLeft WrongIndexRequestedError(indexStatus)
    }

    @Test
    internal fun `will return the number of chunks sent for processing`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(offenderSynchroniserService.splitAllOffendersIntoChunks()).thenReturn(
        listOf(
          OffenderPage(1, 1000),
          OffenderPage(2, 1000),
          OffenderPage(3, 1000)
        )
      )

      val result = indexService.populateIndex(BLUE)

      result shouldBeRight 3
    }

    @Test
    internal fun `For each chunk should send a process chunk message`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(offenderSynchroniserService.splitAllOffendersIntoChunks()).thenReturn(
        listOf(
          OffenderPage(1, 1000),
          OffenderPage(2, 1000),
          OffenderPage(3, 1000)
        )
      )

      indexService.populateIndex(BLUE)

      verify(indexQueueService).sendPopulateOffenderPageMessage(OffenderPage(1, 1000))
      verify(indexQueueService).sendPopulateOffenderPageMessage(OffenderPage(2, 1000))
      verify(indexQueueService).sendPopulateOffenderPageMessage(OffenderPage(3, 1000))
    }
  }

  @Nested
  inner class PopulateIndexWithOffenderPage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING))
      whenever(offenderSynchroniserService.getAllOffenderIdentifiersInPage(any()))
        .thenReturn(listOf(OffenderIdentifier(crn = "X12345"), OffenderIdentifier(crn = "A12345")))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      indexService.populateIndexWithOffenderPage(OffenderPage(page = 99, pageSize = 1000))

      verify(offenderSynchroniserService).getAllOffenderIdentifiersInPage(OffenderPage(page = 99, pageSize = 1000))
    }

    @Test
    internal fun `for each offender will send populate offender message`() {
      indexService.populateIndexWithOffenderPage(OffenderPage(page = 99, pageSize = 1000))

      verify(indexQueueService).sendPopulateOffenderMessage("X12345")
      verify(indexQueueService).sendPopulateOffenderMessage("A12345")
    }
  }

  @Nested
  inner class PopulateIndexWithOffender {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderSynchroniserService.synchroniseOffender(any(), any()))
        .thenReturn(
          """{
            | "offenderId": 99
            |}""".trimMargin().right()
        )
    }

    @Test
    internal fun `will return error if other index is not building`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndexWithOffender("X12345")

      result shouldBeLeft BuildNotInProgressError(indexStatus)
    }

    @Test
    internal fun `will return offender just indexed`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndexWithOffender("X12345")

      result shouldBeRight """{
            | "offenderId": 99
            |}""".trimMargin()
    }

    @Test
    internal fun `will synchronise offender to current building index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      indexService.populateIndexWithOffender("X12345")

      verify(offenderSynchroniserService).synchroniseOffender("X12345", BLUE)
    }
  }

  @Nested
  inner class CountIndex {
    @Test
    fun `Should return count from elasticsearch client`() {
      whenever(elasticSearchClient.count(any(), any())).thenReturn(CountResponse(10L, null, null))

      assertThat(indexService.getIndexCount(BLUE)).isEqualTo(10L)
    }

    @Test
    fun `Should return negative count from elasticsearch client for missing index`() {
      whenever(elasticSearchClient.count(any(), any())).thenThrow(ElasticsearchStatusException("no such index [probation-search-green]", null, null))

      assertThat(indexService.getIndexCount(BLUE)).isEqualTo(-1L)
    }
  }

  @Nested
  inner class UpdateOffender {
    @Test
    fun `No active indexes, update is not requested`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus.newIndex())

      indexService.updateOffender("SOME_CRN")

      verifyZeroInteractions(offenderSynchroniserService)
    }

    @Test
    fun `No active indexes, error is returned`() {
      val indexStatus = IndexStatus.newIndex()
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.updateOffender("SOME_CRN")

      result shouldBeLeft NoActiveIndexesError(indexStatus)
    }

    @Test
    fun `Current index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(offenderSynchroniserService.synchroniseOffender(any(), any())).thenReturn("".right())

      indexService.updateOffender("SOME_CRN")

      verify(offenderSynchroniserService).synchroniseOffender("SOME_CRN", indexStatus.currentIndex)
    }

    @Test
    fun `Other index active, offender is updated`() {
      val indexStatus = IndexStatus(currentIndex = NONE, otherIndexState = BUILDING, currentIndexState = ABSENT)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(offenderSynchroniserService.synchroniseOffender(any(), any())).thenReturn("".right())

      indexService.updateOffender("SOME_CRN")

      verify(offenderSynchroniserService).synchroniseOffender("SOME_CRN", indexStatus.otherIndex)
    }

    @Test
    fun `Both indexes active, offender is updated on both indexes`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, otherIndexState = BUILDING, currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(offenderSynchroniserService.synchroniseOffender(any(), any())).thenReturn("".right())

      indexService.updateOffender("SOME_CRN")

      verify(offenderSynchroniserService).synchroniseOffender(eq("SOME_CRN"), eq(GREEN), eq(BLUE))
    }
  }
}
