package uk.gov.justice.digital.hmpps.indexer.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE

internal class OffenderSynchronizerServiceTest {
  private val communityApi = mock<CommunityService>()
  private val offenderRepository = mock<OffenderRepository>()
  private val indexStatusService = mock<IndexStatusService>()
  private val service = OffenderSynchronizerService(communityApi, offenderRepository, indexStatusService)

  @BeforeEach
  internal fun setUp() {
    whenever(communityApi.getOffender(any())).thenReturn(anOffender())
    whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(IndexStatus.newIndex())
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
    assertThat(index.currentIndex).isEqualTo(BLUE)

    whenever(indexStatusService.getOrCreateCurrentIndexStatus()).thenReturn(index)

    service.synchroniseOffender("X12345")

    verify(offenderRepository).save(isA(), check { assertThat(it.currentIndex).isEqualTo(BLUE) })
  }
}

