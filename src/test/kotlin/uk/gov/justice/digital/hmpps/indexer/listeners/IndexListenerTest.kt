package uk.gov.justice.digital.hmpps.indexer.listeners

import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.integration.readResourceAsText
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.service.IndexService

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
      """.trimIndent(), mock())

      verify(indexService).populateIndex(GREEN)
    }
  }
}
