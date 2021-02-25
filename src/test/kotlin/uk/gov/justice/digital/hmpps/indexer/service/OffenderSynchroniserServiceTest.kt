package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository

internal class OffenderSynchroniserServiceTest {
  private val communityApi = mock<CommunityService>()
  private val offenderRepository = mock<OffenderRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val service = OffenderSynchroniserService(communityApi, offenderRepository, telemetryClient, 10)

  @Nested
  inner class SynchroniseOffender {

    @BeforeEach
    internal fun setUp() {
      whenever(communityApi.getOffenderSearchDetails(any()))
        .thenReturn(
          Offender(
            """{"offenderId": 99, "otherIds": {"crn": "X12345"}}"""
          ).right()
        )
    }

    @Test
    fun `should retrieve offender`() {
      service.synchroniseOffender("X12345", GREEN)

      verify(communityApi).getOffenderSearchDetails("X12345")
    }

    @Test
    internal fun `will save offender to repository`() {
      service.synchroniseOffender("X12345", GREEN)

      verify(offenderRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save offender to current index`() {
      service.synchroniseOffender("X12345", GREEN)

      verify(offenderRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    internal fun `will send telemetry event for update`() {
      service.synchroniseOffender("X12345", GREEN)

      verify(telemetryClient).trackEvent(eq("OFFENDER_UPDATED"), any(), isNull())
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

  @Nested
  inner class SplitAllOffendersIntoChunks {
    @Test
    internal fun `will split total list by our page size`() {
      whenever(communityApi.getCountAllOffenders()).thenReturn(OffendersPage(30, 1, listOf(OffenderIdentifier("X12345"))))

      val chunks = service.splitAllOffendersIntoChunks()
      assertThat(chunks).containsExactly(
        OffenderPage(0, 10),
        OffenderPage(1, 10),
        OffenderPage(2, 10)
      )
    }

    @Test
    internal fun `will round up last page to page size`() {
      whenever(communityApi.getCountAllOffenders()).thenReturn(OffendersPage(31, 1, listOf(OffenderIdentifier("X12345"))))

      var chunks = service.splitAllOffendersIntoChunks()
      assertThat(chunks).containsExactly(
        OffenderPage(0, 10),
        OffenderPage(1, 10),
        OffenderPage(2, 10),
        OffenderPage(3, 10)
      )

      whenever(communityApi.getCountAllOffenders()).thenReturn(OffendersPage(29, 1, listOf(OffenderIdentifier("X12345"))))

      chunks = service.splitAllOffendersIntoChunks()
      assertThat(chunks).containsExactly(
        OffenderPage(0, 10),
        OffenderPage(1, 10),
        OffenderPage(2, 10)
      )
    }

    @Test
    internal fun `will create a large number of pages for a large number of offenders`() {
      val service = OffenderSynchroniserService(communityApi, offenderRepository, telemetryClient, 1000)

      whenever(communityApi.getCountAllOffenders()).thenReturn(OffendersPage(2_000_001, 1, listOf(OffenderIdentifier("X12345"))))

      val chunks = service.splitAllOffendersIntoChunks()
      assertThat(chunks).hasSize(2001)
    }

    @Test
    internal fun `will create a single pages for a tiny number of offenders`() {
      val service = OffenderSynchroniserService(communityApi, offenderRepository, telemetryClient, 1000)

      whenever(communityApi.getCountAllOffenders()).thenReturn(OffendersPage(1, 1, listOf(OffenderIdentifier("X12345"))))

      val chunks = service.splitAllOffendersIntoChunks()
      assertThat(chunks).hasSize(1)
    }

    @Test
    internal fun `will send a telemetry event`() {
      val service = OffenderSynchroniserService(communityApi, offenderRepository, telemetryClient, 1000)
      whenever(communityApi.getCountAllOffenders()).thenReturn(OffendersPage(1, 1, listOf(OffenderIdentifier("X12345"))))

      service.splitAllOffendersIntoChunks()

      verify(telemetryClient).trackEvent(TelemetryEvents.POPULATE_OFFENDER_PAGES.name, mapOf("totalNumberOfOffenders" to "1", "pageSize" to "1000"), null)
    }

    @Test
    internal fun `will create no pages for no offenders`() {
      val service = OffenderSynchroniserService(communityApi, offenderRepository, telemetryClient, 1000)

      whenever(communityApi.getCountAllOffenders()).thenReturn(OffendersPage(0, 0, listOf()))

      val chunks = service.splitAllOffendersIntoChunks()
      assertThat(chunks).hasSize(0)
    }
  }

  @Nested
  inner class GetAllOffenderIdentifiersInPage {
    @BeforeEach
    internal fun setUp() {
      whenever(communityApi.getPageOfOffenders(any(), any())).thenReturn(
        OffendersPage(
          30,
          1,
          listOf(
            OffenderIdentifier("X12345"),
            OffenderIdentifier("X12346"),
            OffenderIdentifier("X12347"),
            OffenderIdentifier("X12348")
          )
        )
      )
    }

    @Test
    internal fun `will pass through page numbers`() {
      service.getAllOffenderIdentifiersInPage(OffenderPage(3, 1000))
      verify(communityApi).getPageOfOffenders(3, 1000)
    }

    @Test
    internal fun `will map each identifier`() {
      val offenders = service.getAllOffenderIdentifiersInPage(OffenderPage(3, 1000))

      assertThat(offenders).containsExactly(
        OffenderIdentifier("X12345"),
        OffenderIdentifier("X12346"),
        OffenderIdentifier("X12347"),
        OffenderIdentifier("X12348")
      )
    }
  }
}
