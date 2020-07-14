package uk.gov.justice.digital.hmpps.indexer.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IndexServiceTest {
  private val offenderSynchronizerService = mock<OffenderSynchronizerService>()
  private val service = IndexService(offenderSynchronizerService)

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

  @Nested
  inner class IndexOffender {
    @BeforeEach
    internal fun setUp() {
      whenever(offenderSynchronizerService.synchroniseOffender(any())).thenReturn("""{"offenderId": 99}""")
    }

    @Test
    internal fun `will delegate to synchronisation service`() {
      service.indexOffender("X12345")

      verify(offenderSynchronizerService).synchroniseOffender("X12345")
    }
  }
}