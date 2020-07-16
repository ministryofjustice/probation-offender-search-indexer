package uk.gov.justice.digital.hmpps.indexer.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.helpers.indexStatus
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import java.util.Optional

class IndexStatusServiceTest {

  private val indexStatusRepository = mock<IndexStatusRepository>()
  private val indexStatusService = IndexStatusService(indexStatusRepository)

  @Nested
  inner class GetCurrentIndexStatus {

    @Test
    fun `An existing index status should be returned`() {
      val existingIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexStatus))

      val actualIndexStatus = indexStatusService.getIndexStatus()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      assertThat(actualIndexStatus).isEqualTo(existingIndexStatus)
    }

    @Test
    fun `A missing index status should create a new one and return it`() {
      val expectedNewIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.NEW)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.empty())
      whenever(indexStatusRepository.save<IndexStatus>(any())).thenReturn(expectedNewIndexStatus)

      val actualIndexStatus = indexStatusService.getIndexStatus()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus).isEqualTo(expectedNewIndexStatus)
      })
      assertThat(actualIndexStatus).isEqualTo(expectedNewIndexStatus)
    }

  }

  @Nested
  inner class MarkBuildInProgress {

    @Test
    fun `Already building index does nothing`() {
      val existingIndexInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Not currently building index saves status building`() {
      val existingIndexNotInProgress = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.NEW)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.otherIndex).isEqualTo(SyncIndex.BLUE)
        assertThat(savedIndexStatus.otherIndexStartBuildTime).isNotNull()
        assertThat(savedIndexStatus.otherIndexState).isEqualTo(IndexState.BUILDING)
      })
    }
  }

  @Nested
  inner class MarkBuildComplete {

    @Test
    fun `Not currently building index does nothing`() {
      val existingIndexNotInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Currently building index updates repository to completed`() {
      val existingIndexInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildCompleteAndSwitchIndex()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.GREEN)
        assertThat(savedIndexStatus.currentIndex).isNotNull()
        assertThat(savedIndexStatus.currentIndexState).isEqualTo(IndexState.COMPLETED)
      })
    }
  }

  @Nested
  inner class MarkBuildCancelled {

    @Test
    fun `Build not currently in progress does nothing`() {
      val existingIndexNotInProgress = indexStatus(otherIndex =  SyncIndex.GREEN, otherIndexState = IndexState.COMPLETED)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Build currently in progress updates repository to cancelled`() {
      val existingIndexInProgress = indexStatus(otherIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildCancelled()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.otherIndex).isEqualTo(SyncIndex.GREEN)
        assertThat(savedIndexStatus.otherIndexEndBuildTime).isNotNull()
        assertThat(savedIndexStatus.otherIndexState).isEqualTo(IndexState.CANCELLED)
      })
    }
  }
}