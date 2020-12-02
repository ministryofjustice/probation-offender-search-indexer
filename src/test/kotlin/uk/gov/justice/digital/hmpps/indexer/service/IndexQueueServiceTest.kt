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
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN

internal class IndexQueueServiceTest {

  private val indexAwsSqsClient = mock<AmazonSQS>()
  private val indexAwsSqsDlqClient = mock<AmazonSQS>()
  private lateinit var indexQueueService: IndexQueueService

  @BeforeEach
  internal fun setUp() {
    whenever(indexAwsSqsClient.getQueueUrl("index-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-queue"))
    whenever(indexAwsSqsDlqClient.getQueueUrl("index-dlq")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:index-dlq"))
    indexQueueService = IndexQueueService(
      indexAwsSqsClient = indexAwsSqsClient,
      indexAwsSqsDlqClient = indexAwsSqsDlqClient,
      indexQueueName = "index-queue",
      indexDlqName = "index-dlq",
      gson = Gson()
    )
  }

  @Nested
  inner class SendIndexRequestMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexAwsSqsClient.sendMessage(any())).thenReturn(SendMessageResult().withMessageId("abc"))
      indexQueueService.sendPopulateIndexMessage(GREEN)
    }

    @Test
    fun `will send message with index name`() {
      verify(indexAwsSqsClient).sendMessage(
        check {
          assertThatJson(it.messageBody).isEqualTo(
            """{
          "type": "POPULATE_INDEX",
          "index": "GREEN"
          }
            """.trimIndent()
          )
        }
      )
    }

    @Test
    fun `will send message to index queue`() {
      verify(indexAwsSqsClient).sendMessage(
        check {
          assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:index-queue")
        }
      )
    }
  }

  @Nested
  inner class SendPopulateOffenderPageMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexAwsSqsClient.sendMessage(any())).thenReturn(SendMessageResult().withMessageId("abc"))
      indexQueueService.sendPopulateOffenderPageMessage(OffenderPage(1, 1000))
    }

    @Test
    fun `will send message with index name`() {
      verify(indexAwsSqsClient).sendMessage(
        check {
          assertThatJson(it.messageBody).isEqualTo(
            """{
          "type": "POPULATE_OFFENDER_PAGE",
          "offenderPage": {
            "page": 1,
            "pageSize": 1000
          }
        }
            """.trimIndent()
          )
        }
      )
    }

    @Test
    fun `will send message to index queue`() {
      verify(indexAwsSqsClient).sendMessage(
        check {
          assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:index-queue")
        }
      )
    }
  }

  @Nested
  inner class SendPopulateOffenderMessage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexAwsSqsClient.sendMessage(any())).thenReturn(SendMessageResult().withMessageId("abc"))
      indexQueueService.sendPopulateOffenderMessage("X12345")
    }

    @Test
    fun `will send message with crn`() {
      verify(indexAwsSqsClient).sendMessage(
        check {
          assertThatJson(it.messageBody).isEqualTo(
            """
        {
          "type":"POPULATE_OFFENDER",
          "crn":"X12345"
        }
            """.trimIndent()
          )
        }
      )
    }

    @Test
    fun `will send message to index queue`() {
      verify(indexAwsSqsClient).sendMessage(
        check {
          assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:index-queue")
        }
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class IndexQueueStatusActive {
    fun activeTestSource() = listOf(
      Arguments.of(0, 0, 0, false),
      Arguments.of(1, 0, 0, true),
      Arguments.of(0, 1, 0, true),
      Arguments.of(0, 0, 1, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 1, 0, true),
      Arguments.of(0, 1, 1, true),
      Arguments.of(1, 0, 1, true),
      Arguments.of(1, 1, 1, true)
    )

    @ParameterizedTest
    @MethodSource("activeTestSource")
    fun `index queue status active`(messagesOnQueue: Int, messagesOnDlq: Int, messagesInFlight: Int, expectedActive: Boolean) {
      assertThat(IndexQueueStatus(messagesOnQueue, messagesOnDlq, messagesInFlight).active).isEqualTo(expectedActive)
    }
  }
}
