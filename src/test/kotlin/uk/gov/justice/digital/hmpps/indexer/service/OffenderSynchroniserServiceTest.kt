package uk.gov.justice.digital.hmpps.indexer.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository

internal class OffenderSynchroniserServiceTest {
  private val communityApi = mock<CommunityService>()
  private val offenderRepository = mock<OffenderRepository>()
  private val indexStatusService = mock<IndexStatusService>()
  private val service = OffenderSynchroniserService(communityApi, offenderRepository, indexStatusService)

  @Nested
  inner class SynchroniseOffender {

    @BeforeEach
    internal fun setUp() {
      whenever(communityApi.getOffender(any())).thenReturn(anOffender())
      whenever(indexStatusService.getIndexStatus()).thenReturn(IndexStatus.newIndex())
    }

    @Test
    fun `should retrieve offender`() {
      service.synchroniseOffender("X12345")

      verify(communityApi).getOffender("X12345")
    }

    @Test
    internal fun `will save offender to repository`() {
      service.synchroniseOffender("X12345")

      verify(offenderRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save offender to current index`() {
      val index = IndexStatus.newIndex().toBuildComplete()
      assertThat(index.currentIndex).isEqualTo(GREEN)

      whenever(indexStatusService.getIndexStatus()).thenReturn(index)

      service.synchroniseOffender("X12345")

      verify(offenderRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }
  }

  @Nested
  inner class CheckExistsAndReset {
    @Nested
    inner class IndexExists {
      @BeforeEach
      internal fun setUp() {
        whenever(offenderRepository.doesIndexExist(GREEN)).thenReturn(true)

        service.checkExistsAndReset(GREEN)
      }
      @Test
      internal fun `will delete the index if it exists`() {
        verify(offenderRepository).deleteIndex(GREEN)
      }
      @Test
      internal fun `will recreate the index`() {
        verify(offenderRepository).createIndex(GREEN)
      }
    }
    @Nested
    inner class IndexDoesNotExists {
      @BeforeEach
      internal fun setUp() {
        whenever(offenderRepository.doesIndexExist(GREEN)).thenReturn(false)

        service.checkExistsAndReset(GREEN)
      }
      @Test
      internal fun `won't bother deleting index if it does not exist`() {
        whenever(offenderRepository.doesIndexExist(GREEN)).thenReturn(false)

        service.checkExistsAndReset(GREEN)

        verify(offenderRepository, never()).deleteIndex(any())
      }
      @Test
      internal fun `will create the index`() {
        verify(offenderRepository).createIndex(GREEN)
      }

    }
  }
}

