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
import java.util.*

class IndexServiceTest {

  private val indexStatusRepository = mock<IndexStatusRepository>()
  private val indexService = IndexService(indexStatusRepository)

  @Nested
  inner class GetCurrentIndex() {

    @Test
    fun `An existing status should be returned`() {
      val existingIndexStatus = IndexStatus(currentIndex = SyncIndex.BLUE, startIndexTime = LocalDateTime.now().minusHours(2), endIndexTime = null, inProgress = true)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.ofNullable(existingIndexStatus))

      val actualIndexStatus = indexService.getCurrentIndex()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      assertThat(actualIndexStatus).isEqualTo(existingIndexStatus)
    }

    @Test
    fun `A missing index status should create a new one and return it`() {
      val expectedNewIndexStatus = IndexStatus(currentIndex = SyncIndex.GREEN, startIndexTime = null, endIndexTime = null, inProgress = false)
      whenever(indexStatusRepository.findById(INDEX_STATUS_ID)).thenReturn(Optional.empty())
      whenever(indexStatusRepository.save<IndexStatus>(any())).thenReturn(expectedNewIndexStatus)

      val actualIndexStatus = indexService.getCurrentIndex()

      verify(indexStatusRepository).findById(INDEX_STATUS_ID)
      verify(indexStatusRepository).save<IndexStatus>(check {
        assertThat(it).isEqualTo(expectedNewIndexStatus)
      })
      assertThat(actualIndexStatus).isEqualTo(expectedNewIndexStatus)
    }

  }

  // buildIndex
  @Nested
  inner class BuildIndex {

  }

  // markIndexingComplete
  @Nested
  inner class MarkIndexingComplete {

  }

  // cancelIndexing
  @Nested
  inner class CancelIndexing {

  }

  // indexOffender
  @Nested
  inner class IndexOffender {

  }

}