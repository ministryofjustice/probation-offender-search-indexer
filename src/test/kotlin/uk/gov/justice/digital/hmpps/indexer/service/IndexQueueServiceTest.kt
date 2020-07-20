package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.SendMessageResult
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN

internal class IndexQueueServiceTest {

  private val client = mock<AmazonSQS>()
  private lateinit var indexQueueService : IndexQueueService
  @BeforeEach
  internal fun setUp() {
    whenever(client.getQueueUrl("index-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-queue"))
    indexQueueService = IndexQueueService(client, "index-queue", Gson())
  }


  @Nested
  inner class SendIndexRequestMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(client.sendMessage(any())).thenReturn(SendMessageResult().withMessageId("abc"))
      indexQueueService.sendPopulateIndexMessage(GREEN)
    }

    @Test
    fun `will send message with index name`() {
      verify(client).sendMessage(check {
        assertThatJson(it.messageBody).isEqualTo("""{
          "type": "POPULATE_INDEX",
          "index": "GREEN"
          }
        """.trimIndent())
      })
    }

    @Test
    fun `will send message to index queue`() {
      verify(client).sendMessage(check {
        assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:index-queue")
      })
    }
  }
  @Nested
  inner class SendPopulateOffenderPageMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(client.sendMessage(any())).thenReturn(SendMessageResult().withMessageId("abc"))
      indexQueueService.sendPopulateOffenderPageMessage(OffenderPage(1, 1000))
    }

    @Test
    fun `will send message with index name`() {
      verify(client).sendMessage(check {
        assertThatJson(it.messageBody).isEqualTo("""{
          "type": "POPULATE_OFFENDER_PAGE",
          "offenderPage": {
            "page": 1,
            "pageSize": 1000
          }
        }
        """.trimIndent())
      })
    }

    @Test
    fun `will send message to index queue`() {
      verify(client).sendMessage(check {
        assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:index-queue")
      })
    }
  }
}