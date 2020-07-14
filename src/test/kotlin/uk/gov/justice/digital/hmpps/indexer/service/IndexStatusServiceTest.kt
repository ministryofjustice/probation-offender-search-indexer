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
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import java.time.LocalDateTime
import java.util.Optional

class IndexStatusServiceTest {

  private val indexStatusRepository = mock<IndexStatusRepository>()
  private val indexStatusService = IndexStatusService(indexStatusRepository)

  @Nested
  inner class GetCurrentIndexStatus {

    @Test
    fun `An existing index status should be returned`() {
      val existingIndexStatus = indexInProgress(SyncIndex.BLUE)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexStatus))

      val actualIndexStatus = indexStatusService.getOrCreateCurrentIndexStatus()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      assertThat(actualIndexStatus).isEqualTo(existingIndexStatus)
    }

    @Test
    fun `A missing index status should create a new one and return it`() {
      val expectedNewIndexStatus = indexNotInProgress(SyncIndex.GREEN)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.empty())
      whenever(indexStatusRepository.save<IndexStatus>(any())).thenReturn(expectedNewIndexStatus)

      val actualIndexStatus = indexStatusService.getOrCreateCurrentIndexStatus()

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
      val existingIndexInProgress = indexInProgress(SyncIndex.GREEN)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Not currently building index saves status building`() {
      val existingIndexNotInProgress = indexNotInProgress(SyncIndex.BLUE)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.BLUE)
        assertThat(savedIndexStatus.startIndexTime).isNotNull()
        assertThat(savedIndexStatus.inProgress).isTrue()
      })
    }
  }

  @Nested
  inner class MarkBuildComplete {

    @Test
    fun `Not currently building index does nothing`() {
      val existingIndexNotInProgress = indexNotInProgress(SyncIndex.GREEN)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Currently building index updates repository to not building`() {
      val existingIndexInProgress = indexInProgress(SyncIndex.GREEN)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildComplete()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.BLUE)
        assertThat(savedIndexStatus.endIndexTime).isNotNull()
        assertThat(savedIndexStatus.inProgress).isFalse()
      })
    }
  }

  @Nested
  inner class MarkBuildCancelled {

    @Test
    fun `Build not currently in progress does nothing`() {
      val existingIndexNotInProgress = indexNotInProgress(SyncIndex.GREEN)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      verify(indexStatusRepository, never()).save<IndexStatus>(any())
    }

    @Test
    fun `Build currently in progress updates repository to not building`() {
      val existingIndexInProgress = indexInProgress(SyncIndex.GREEN)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.markBuildCancelled()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.BLUE)
        assertThat(savedIndexStatus.endIndexTime).isNull()
        assertThat(savedIndexStatus.inProgress).isFalse()
      })
    }
  }

  private fun indexNotInProgress(currentIndex: SyncIndex): IndexStatus {
    return IndexStatus(currentIndex = currentIndex, startIndexTime = null, endIndexTime = null, inProgress = false)
  }

  private fun indexInProgress(currentIndex: SyncIndex): IndexStatus {
    return IndexStatus(currentIndex = currentIndex, startIndexTime = LocalDateTime.now().minusHours(1), endIndexTime = null, inProgress = true)
  }
}