package uk.gov.justice.digital.hmpps.indexer.listeners

import arrow.core.left
import arrow.core.right
import ch.qos.logback.classic.Level
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.service.BuildNotInProgressError
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.OffenderPage

internal class IndexListenerTest {
  val indexService = mock<IndexService>()
  val listener = IndexListener(Gson(), indexService)

  @Nested
  inner class PopulateIndex {
    @Test
    internal fun `will call service with index name`() {
      whenever(indexService.populateIndex(GREEN)).thenReturn(1.right())

      listener.processIndexRequest("""
      {
        "type": "POPULATE_INDEX",
        "index": "GREEN"
      }
      """.trimIndent())

      verify(indexService).populateIndex(GREEN)
    }

    @Test
    internal fun `failed request`() {
      val logAppender = findLogAppender(IndexListener::class.java)
      whenever(indexService.populateIndex(GREEN)).thenReturn(BuildNotInProgressError(IndexStatus.newIndex()).left())

      listener.processIndexRequest("""
      {
        "type": "POPULATE_INDEX",
        "index": "GREEN"
      }
      """.trimIndent())

      assertThat(logAppender.list).anyMatch { it.message.contains("failed with error") }
    }
  }

  @Nested
  inner class PopulateOffenderPage {
    @Test
    internal fun `will call service with page details`() {
      whenever(indexService.populateIndexWithOffenderPage(any())).thenReturn(Unit.right())

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
      whenever(indexService.populateIndexWithOffender(any())).thenReturn("{}".right())

      listener.processIndexRequest("""
      {
        "type": "POPULATE_OFFENDER",
        "crn": "X12345"
      }
      """.trimIndent())

      verify(indexService).populateIndexWithOffender("X12345")
    }
  }

  @Nested
  inner class BadMessages {
    @Test
    internal fun `will fail for bad json`() {
      val logAppender = findLogAppender(IndexListener::class.java)

      assertThatThrownBy { listener.processIndexRequest("this is bad json") }
          .isInstanceOf(JsonSyntaxException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Failed to process message") && it.level == Level.ERROR }
    }

    @Test
    internal fun `will fail for unknown message type`() {
      val logAppender = findLogAppender(IndexListener::class.java)

      assertThatThrownBy {
        listener.processIndexRequest("""
            {
              "type": "THIS_IS_AN_UNEXPECTED_MESSAGE_TYPE",
              "crn": "X12345"
            }
            """.trimIndent())
      }.isInstanceOf(IllegalArgumentException::class.java)

      assertThat(logAppender.list).anyMatch { it.message.contains("Unknown request type for message") && it.level == Level.ERROR }
    }
  }
}
