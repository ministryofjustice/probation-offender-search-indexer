package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.getOrHandle
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.helpers.indexStatus
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

class IndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val offenderSynchroniserService = mock<OffenderSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val indexService = IndexService(indexStatusService, offenderSynchroniserService, indexQueueService)

  @Nested
  inner class BuildIndex {

    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(BuildIndexError.BuildAlreadyInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.NEW))

      indexService.prepareIndexForRebuild()

      verify(indexStatusService).markBuildInProgress()
    }

    @Test
    fun `A request is made to reset the other index`() {
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.NEW))

      indexService.prepareIndexForRebuild()

      verify(offenderSynchroniserService).checkExistsAndReset(SyncIndex.BLUE)
    }

    @Test
    fun `A request is made to build other index`() {
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.NEW))

      indexService.prepareIndexForRebuild()

      verify(indexQueueService).sendPopulateIndexMessage(any())
    }

    @Test
    fun `The updated index is returned`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(SyncIndex.GREEN, IndexState.NEW))
          .thenReturn(expectedIndexStatus)

      val result = indexService.prepareIndexForRebuild()

      verify(indexStatusService, times(2)).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(expectedIndexStatus)
    }

  }

  @Nested
  inner class MarkIndexingComplete {

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.markIndexingComplete()

      verify(indexStatusService).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(MarkBuildCompleteError.BuildNotInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index state as complete`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.markIndexingComplete()

      verify(indexStatusService).markBuildCompleteAndSwitchIndex()
    }

    @Test
    fun `A request is made to remove queued index requests`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.markIndexingComplete()

      verify(indexQueueService).clearAllMessages()
    }

    @Test
    fun `Once current index marked as complete, the 'other' index is current`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING))
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
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.cancelIndexing()

      verify(indexStatusService).getIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(CancelBuildIndexError.BuildNotInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index state as cancelled`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.cancelIndexing()

      verify(indexStatusService).markBuildCancelled()
    }

    @Test
    fun `Once current index marked as cancelled, the 'other' index is current`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.CANCELLED)
      whenever(indexStatusService.getIndexStatus())
          .thenReturn(indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING))
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
      whenever(offenderSynchroniserService.synchroniseOffender(any())).thenReturn("""{"offenderId": 99}""")
    }

    @Test
    internal fun `will delegate to synchronisation service`() {
      indexService.indexOffender("X12345")

      verify(offenderSynchroniserService).synchroniseOffender("X12345")
    }
  }

}