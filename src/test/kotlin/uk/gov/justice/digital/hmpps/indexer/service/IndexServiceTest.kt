package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.getOrHandle
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.helpers.indexStatus
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.service.PopulateIndexError.BuildNotInProgress
import uk.gov.justice.digital.hmpps.indexer.service.PopulateIndexError.WrongIndexRequested

class IndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val offenderSynchroniserService = mock<OffenderSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val indexService = IndexService(indexStatusService, offenderSynchroniserService, indexQueueService)

  @Nested
  inner class BuildIndex {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.initialiseIndexWhenRequired()).thenReturn(indexStatusService)
    }

    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(BuildIndexError.BuildAlreadyInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = BLUE, otherIndexState = IndexState.NEW))

      indexService.prepareIndexForRebuild()

      verify(indexStatusService).markBuildInProgress()
    }

    @Test
    fun `A request is made to reset the other index`() {
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = BLUE, otherIndexState = IndexState.NEW))

      indexService.prepareIndexForRebuild()

      verify(offenderSynchroniserService).checkExistsAndReset(BLUE)
    }

    @Test
    fun `A request is made to build other index`() {
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = BLUE, otherIndexState = IndexState.NEW))

      indexService.prepareIndexForRebuild()

      verify(indexQueueService).sendPopulateIndexMessage(any())
    }

    @Test
    fun `The updated index is returned`() {
      val expectedIndexStatus = indexStatus(otherIndex = GREEN, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(GREEN, IndexState.NEW))
          .thenReturn(expectedIndexStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService, times(2)).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(expectedIndexStatus)
    }

  }

  @Nested
  inner class MarkIndexingComplete {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.markBuildCompleteAndSwitchIndex()).thenReturn(indexStatus(otherIndex = BLUE, otherIndexState = COMPLETED))
    }

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.markIndexingComplete()

      verify(indexStatusService).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(MarkBuildCompleteError.BuildNotInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index state as complete`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.markIndexingComplete()

      verify(indexStatusService).markBuildCompleteAndSwitchIndex()
    }

    @Test
    fun `A request is made to remove queued index requests`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.markIndexingComplete()

      verify(indexQueueService).clearAllMessages()
    }

    @Test
    fun `Once current index marked as complete, the 'other' index is current`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = BLUE, otherIndexState = BUILDING))
          .thenReturn(expectedIndexStatus)

      val result = indexService.markIndexingComplete()

      verify(indexStatusService, times(2)).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(expectedIndexStatus)
    }
  }

  @Nested
  inner class CancelIndexing {

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.cancelIndexing()

      verify(indexStatusService).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(CancelBuildIndexError.BuildNotInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index state as cancelled`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(indexStatusService).markBuildCancelled()
    }

    @Test
    fun `Once current index marked as cancelled, the 'other' index is current`() {
      val expectedIndexStatus = indexStatus(otherIndex = BLUE, otherIndexState = IndexState.CANCELLED)
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = BLUE, otherIndexState = BUILDING))
          .thenReturn(expectedIndexStatus)

      val result = indexService.cancelIndexing()

      verify(indexStatusService, times(2)).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(expectedIndexStatus)
    }

  }

  @Nested
  inner class IndexOffender {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderSynchroniserService.synchroniseOffender(any(), any())).thenReturn("""{"offenderId": 99}""")
    }

    @Test
    internal fun `will delegate to synchronisation service`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      indexService.indexOffender("X12345")

      verify(offenderSynchroniserService).synchroniseOffender("X12345", GREEN)
    }
  }

  @Nested
  inner class PopulateIndex {

    @Test
    internal fun `will return an error if indexing is not in progress`() {
      val indexStatus = IndexStatus.newIndex().toBuildInProgress().toBuildComplete()
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndex(GREEN)

      result shouldBeLeft BuildNotInProgress(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing request is for the wrong index`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)

      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndex(GREEN)

      result shouldBeLeft WrongIndexRequested(indexStatus)
    }

    @Test
    internal fun `will return the number of chunks sent for processing`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(offenderSynchroniserService.splitAllOffendersIntoChunks()).thenReturn(listOf(
          OffenderPage(1, 1000),
          OffenderPage(2, 1000),
          OffenderPage(3, 1000)
      ))

      val result = indexService.populateIndex(BLUE)

      result shouldBeRight 3
    }

    @Test
    internal fun `For each chunk should send a process chunk message`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
      whenever(offenderSynchroniserService.splitAllOffendersIntoChunks()).thenReturn(listOf(
          OffenderPage(1, 1000),
          OffenderPage(2, 1000),
          OffenderPage(3, 1000)
      ))

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
      whenever(offenderSynchroniserService.getAllOffenderIdentifiersInPage(any()))
          .thenReturn(listOf(OffenderIdentifier(crn = "X12345"), OffenderIdentifier(crn = "A12345")))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      indexService.populateIndexWithOffenderPage(OffenderPage(page = 99, pageSize = 1000))

      verify(offenderSynchroniserService).getAllOffenderIdentifiersInPage(OffenderPage(page = 99, pageSize = 1000))
    }

    @Test
    internal fun `for each offender will send populate offeder message`() {
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
          .thenReturn("""{
            | "offenderId": 99
            |}""".trimMargin())
    }

    @Test
    internal fun `will return error if other index is not building`() {
      val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, otherIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      val result = indexService.populateIndexWithOffender("X12345")

      result shouldBeLeft BuildNotInProgress(indexStatus)
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

}