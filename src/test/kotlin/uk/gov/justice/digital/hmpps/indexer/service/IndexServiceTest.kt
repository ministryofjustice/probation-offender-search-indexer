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
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import java.time.LocalDateTime

class IndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val offenderSynchroniserService = mock<OffenderSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val indexService = IndexService(indexStatusService, offenderSynchroniserService, indexQueueService)

  @Nested
  inner class BuildIndex {

    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = indexStatus(SyncIndex.GREEN, IndexState.BUILDING)
      whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.buildIndex()

      verify(indexStatusService).getOrCreateCurrentIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(BuildIndexError.BuildAlreadyInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(indexStatus(SyncIndex.GREEN, IndexState.NEW))

      indexService.buildIndex()

      verify(indexStatusService).markBuildInProgress()
    }

    @Test
    fun `A request is made to reset the other index`() {
      whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(indexStatus(SyncIndex.GREEN, IndexState.NEW))

      indexService.buildIndex()

      verify(offenderSynchroniserService).checkExistsAndReset(SyncIndex.BLUE)
    }

    @Test
    fun `A request is made to build other index`() {
      whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(indexStatus(SyncIndex.GREEN, IndexState.NEW))

      indexService.buildIndex()

      verify(indexQueueService).sendIndexRequestMessage()
    }

    @Test
    fun `The updated index is returned`() {
      val expectedIndexStatus = indexStatus(SyncIndex.BLUE, IndexState.BUILDING)
      whenever(indexStatusService.getOrCreateCurrentIndexStatus())
          .thenReturn(indexStatus(SyncIndex.GREEN, IndexState.NEW))
          .thenReturn(expectedIndexStatus)

      val result = indexService.buildIndex()

      verify(indexStatusService, times(2)).getOrCreateCurrentIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(expectedIndexStatus)
    }

  }

  @Nested
  inner class MarkIndexingComplete {

    @Test
    fun `Index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = SyncIndex.GREEN, state = IndexState.COMPLETED, startIndexTime = LocalDateTime.now().minusHours(2), endIndexTime = LocalDateTime.now().minusHours(1))
      whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(expectedIndexStatus)

      val result = indexService.markIndexingComplete()

      verify(indexStatusService).getOrCreateCurrentIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(MarkBuildCompleteError.BuildNotInProgress(expectedIndexStatus))
    }

    @Test
    fun `A request is made to mark the index state as complete`() {
      val expectedIndexStatus = indexStatus(currentIndex = SyncIndex.GREEN, state = IndexState.BUILDING)
      whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.markIndexingComplete()

      verify(indexStatusService).markBuildComplete()
    }

    @Test
    fun `A request is made to remove queued index requests`() {
      val expectedIndexStatus = indexStatus(currentIndex = SyncIndex.GREEN, state = IndexState.BUILDING)
      whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(expectedIndexStatus)

      indexService.markIndexingComplete()

      verify(indexQueueService).clearAllMessages()
    }

    @Test
    fun `Once current index marked as complete, the 'other' index is current`() {
      val expectedIndexStatus = indexStatus(currentIndex = SyncIndex.GREEN, state = IndexState.COMPLETED)
      whenever(indexStatusService.getOrCreateCurrentIndexStatus())
          .thenReturn(indexStatus(currentIndex = SyncIndex.BLUE, state = IndexState.BUILDING))
          .thenReturn(expectedIndexStatus)

      val result = indexService.markIndexingComplete()

      verify(indexStatusService, times(2)).getOrCreateCurrentIndexStatus()
      assertThat(result.getOrHandle { it }).isEqualTo(expectedIndexStatus)
    }
  }

  // cancelIndexing
  @Nested
  inner class CancelIndexing {

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

  private fun indexStatus(currentIndex: SyncIndex, state: IndexState) =
      IndexStatus(currentIndex = currentIndex, startIndexTime = LocalDateTime.now().minusHours(1), endIndexTime = null, state = state)

}