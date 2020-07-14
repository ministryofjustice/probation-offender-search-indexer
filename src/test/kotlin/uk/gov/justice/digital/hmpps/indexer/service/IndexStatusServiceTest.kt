package uk.gov.justice.digital.hmpps.indexer.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
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
  inner class GetCurrentIndex {

    @Test
    fun `An existing index status should be returned`() {
      val existingIndexStatus = IndexStatus(currentIndex = SyncIndex.BLUE, startIndexTime = LocalDateTime.now().minusHours(2), endIndexTime = null, inProgress = true)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexStatus))

      val actualIndexStatus = indexStatusService.getOrCreateCurrentIndex()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      assertThat(actualIndexStatus).isEqualTo(existingIndexStatus)
    }

    @Test
    fun `A missing index status should create a new one and return it`() {
      val expectedNewIndexStatus = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = null, endIndexTime = null, inProgress = false)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.empty())
      whenever(indexStatusRepository.save<IndexStatus>(any())).thenReturn(expectedNewIndexStatus)

      val actualIndexStatus = indexStatusService.getOrCreateCurrentIndex()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus).isEqualTo(expectedNewIndexStatus)
      })
      assertThat(actualIndexStatus).isEqualTo(expectedNewIndexStatus)
    }

  }

  @Nested
  inner class MarkRebuildInProgress {

    @Test
    fun `Already building index returns false`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = LocalDateTime.now().minusHours(1), endIndexTime = null, inProgress = true)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      assertThat(indexStatusService.markBuildInProgress()).isFalse()
    }

    @Test
    fun `Not currently building index returns true`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = null, endIndexTime = null, inProgress = false)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      assertThat(indexStatusService.markBuildInProgress()).isTrue()
    }

    @Test
    fun `Not currently building index saves status building`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = null, endIndexTime = null, inProgress = false)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      indexStatusService.markBuildInProgress()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.GREEN)
        assertThat(savedIndexStatus.startIndexTime).isNotNull()
        assertThat(savedIndexStatus.inProgress).isTrue()
      })
    }
  }

  @Nested
  inner class MarkBuildComplete {

    @Test
    fun `Not currently building index returns false`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = null, endIndexTime = null, inProgress = false)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      assertThat(indexStatusService.markBuildComplete()).isFalse()
    }

    @Test
    fun `Currently building index returns true`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = LocalDateTime.now().minusHours(1), endIndexTime = null, inProgress = true)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      assertThat(indexStatusService.markBuildComplete()).isTrue()
    }

    @Test
    fun `Currently building index updates repository to not building`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = LocalDateTime.now().minusHours(1), endIndexTime = null, inProgress = true)
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
  inner class CancelIndexing {

    @Test
    fun `Build not currently in progress returns false`() {
      val existingIndexNotInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = null, endIndexTime = null, inProgress = false)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexNotInProgress))

      assertThat(indexStatusService.cancelIndexBuild()).isFalse()
    }

    @Test
    fun `Build currently in progress returns true`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = LocalDateTime.now().minusHours(1), endIndexTime = null, inProgress = true)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      assertThat(indexStatusService.cancelIndexBuild()).isTrue()
    }

    @Test
    fun `Build currently in progress updates repository to not building`() {
      val existingIndexInProgress = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = LocalDateTime.now().minusHours(1), endIndexTime = null, inProgress = true)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexInProgress))

      indexStatusService.cancelIndexBuild()

      verify(indexStatusRepository).save<IndexStatus>(check { savedIndexStatus ->
        assertThat(savedIndexStatus.currentIndex).isEqualTo(SyncIndex.BLUE)
        assertThat(savedIndexStatus.endIndexTime).isNull()
        assertThat(savedIndexStatus.inProgress).isFalse()
      })
    }
  }
}