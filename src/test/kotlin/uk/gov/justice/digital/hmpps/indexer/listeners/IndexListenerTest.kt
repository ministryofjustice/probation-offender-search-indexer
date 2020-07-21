package uk.gov.justice.digital.hmpps.indexer.listeners

import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.OffenderPage

internal class IndexListenerTest {
  val indexService = mock<IndexService>()
  val listener = IndexListener(Gson(), indexService)

  @Nested
  inner class PopulateIndex {
    @Test
    internal fun `will call service with index name`() {
      listener.processIndexRequest("""
      {
        "type": "POPULATE_INDEX",
        "index": "GREEN"
      }
      """.trimIndent())

      verify(indexService).populateIndex(GREEN)
    }
  }

  @Nested
  inner class PopulateOffenderPage {
    @Test
    internal fun `will call service with page details`() {
      listener.processIndexRequest("""
      {
        "type": "POPULATE_OFFENDER_PAGE",
        "offenderPage": {
          "page": 1,
          "pageSize": 1000
        }
      }
      """.trimIndent())

      verify(indexService).populateIndexWithOffenderPage(OffenderPage(1, 1000))
    }
  }
  @Nested
  inner class PopulateOffender {
    @Test
    internal fun `will call service with crn to populate`() {
      listener.processIndexRequest("""
      {
        "type": "POPULATE_OFFENDER",
        "crn": "X12345"
      }
      """.trimIndent())

      verify(indexService).populateIndexWithOffender("X12345")
    }
  }
}
