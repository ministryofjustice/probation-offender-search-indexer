package uk.gov.justice.digital.hmpps.prisontoprobation.services.health

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.springframework.boot.actuate.health.Status
import uk.gov.justice.digital.hmpps.indexer.health.DlqStatus
import uk.gov.justice.digital.hmpps.indexer.health.EventQueueHealth
import uk.gov.justice.digital.hmpps.indexer.health.QueueAttributes.MESSAGES_IN_FLIGHT
import uk.gov.justice.digital.hmpps.indexer.health.QueueAttributes.MESSAGES_ON_DLQ
import uk.gov.justice.digital.hmpps.indexer.health.QueueAttributes.MESSAGES_ON_QUEUE
import uk.gov.justice.digital.hmpps.indexer.health.QueueHealth


class QueueHealthTest {

  private val someQueueName = "some queue name"
  private val someQueueUrl = "some queue url"
  private val someDLQName = "some DLQ name"
  private val someDLQUrl = "some DLQ url"
  private val someMessagesOnQueueCount = 123
  private val someMessagesInFlightCount = 456
  private val someMessagesOnDLQCount = 789
  private val eventAmazonSqs: AmazonSQS = mock()
  private val eventAmazonSqsDLQ: AmazonSQS = mock()
  private val indexAmazonSqs: AmazonSQS = mock()
  private val indexAmazonSqsDLQ: AmazonSQS = mock()
  private val eventQueueHealth: QueueHealth = EventQueueHealth(eventAmazonSqs, eventAmazonSqsDLQ, someQueueName, someDLQName)
  private val indexQueueHealth: QueueHealth = EventQueueHealth(indexAmazonSqs, indexAmazonSqsDLQ, someQueueName, someDLQName)

  @Nested
  inner class EventQueue {
    
    @Test
    fun `health - queue found - UP`() {
      mockHealthyQueue()

      val health = eventQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.UP)
    }

    @Test
    fun `health - attributes returned - included in health status`() {
      mockHealthyQueue()

      val health = eventQueueHealth.health()

      assertThat(health.details[MESSAGES_ON_QUEUE.healthName]).isEqualTo(someMessagesOnQueueCount)
      assertThat(health.details[MESSAGES_IN_FLIGHT.healthName]).isEqualTo(someMessagesInFlightCount)
    }

    @Test
    fun `health - queue not found - DOWN`() {
      whenever(eventAmazonSqs.getQueueUrl(anyString())).thenThrow(QueueDoesNotExistException::class.java)

      val health = eventQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `health - failed to get main queue attributes - DOWN`() {
      whenever(eventAmazonSqs.getQueueUrl(anyString())).thenReturn(someGetQueueUrlResult())
      whenever(eventAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenThrow(RuntimeException::class.java)

      val health = eventQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `health - DLQ UP - reports DLQ UP`() {
      mockHealthyQueue()

      val health = eventQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.UP.description)
    }

    @Test
    fun `health - DLQ attributes returned - included in health status`() {
      mockHealthyQueue()

      val health = eventQueueHealth.health()

      assertThat(health.details[MESSAGES_ON_DLQ.healthName]).isEqualTo(someMessagesOnDLQCount)
    }

    @Test
    fun `health - DLQ down - main queue health also DOWN`() {
      whenever(eventAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(eventAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithoutDLQ())

      val health = eventQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.DOWN)
      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_ATTACHED.description)
    }

    @Test
    fun `health - no RedrivePolicy attribute on main queue - DLQ NOT ATTACHED`() {
      whenever(eventAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(eventAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithoutDLQ())

      val health = eventQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_ATTACHED.description)
    }

    @Test
    fun `health - DLQ not found - DLQ NOT FOUND`() {
      whenever(eventAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(eventAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
      whenever(eventAmazonSqsDLQ.getQueueUrl(someDLQName)).thenThrow(QueueDoesNotExistException::class.java)

      val health = eventQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_FOUND.description)
    }

    @Test
    fun `health - DLQ failed to get attributes - DLQ NOT AVAILABLE`() {
      whenever(eventAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(eventAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
      whenever(eventAmazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ())
      whenever(eventAmazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenThrow(RuntimeException::class.java)

      val health = eventQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_AVAILABLE.description)
    }
  }


  @Nested
  inner class IndexQueue {

    @Test
    fun `health - queue found - UP`() {
      mockHealthyQueue()

      val health = indexQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.UP)
    }

    @Test
    fun `health - attributes returned - included in health status`() {
      mockHealthyQueue()

      val health = indexQueueHealth.health()

      assertThat(health.details[MESSAGES_ON_QUEUE.healthName]).isEqualTo(someMessagesOnQueueCount)
      assertThat(health.details[MESSAGES_IN_FLIGHT.healthName]).isEqualTo(someMessagesInFlightCount)
    }

    @Test
    fun `health - queue not found - DOWN`() {
      whenever(indexAmazonSqs.getQueueUrl(anyString())).thenThrow(QueueDoesNotExistException::class.java)

      val health = indexQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `health - failed to get main queue attributes - DOWN`() {
      whenever(indexAmazonSqs.getQueueUrl(anyString())).thenReturn(someGetQueueUrlResult())
      whenever(indexAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenThrow(RuntimeException::class.java)

      val health = indexQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `health - DLQ UP - reports DLQ UP`() {
      mockHealthyQueue()

      val health = indexQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.UP.description)
    }

    @Test
    fun `health - DLQ attributes returned - included in health status`() {
      mockHealthyQueue()

      val health = indexQueueHealth.health()

      assertThat(health.details[MESSAGES_ON_DLQ.healthName]).isEqualTo(someMessagesOnDLQCount)
    }

    @Test
    fun `health - DLQ down - main queue health also DOWN`() {
      whenever(indexAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(indexAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithoutDLQ())

      val health = indexQueueHealth.health()

      assertThat(health.status).isEqualTo(Status.DOWN)
      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_ATTACHED.description)
    }

    @Test
    fun `health - no RedrivePolicy attribute on main queue - DLQ NOT ATTACHED`() {
      whenever(indexAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(indexAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithoutDLQ())

      val health = indexQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_ATTACHED.description)
    }

    @Test
    fun `health - DLQ not found - DLQ NOT FOUND`() {
      whenever(indexAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(indexAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
      whenever(indexAmazonSqsDLQ.getQueueUrl(someDLQName)).thenThrow(QueueDoesNotExistException::class.java)

      val health = indexQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_FOUND.description)
    }

    @Test
    fun `health - DLQ failed to get attributes - DLQ NOT AVAILABLE`() {
      whenever(indexAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
      whenever(indexAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
      whenever(indexAmazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ())
      whenever(indexAmazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenThrow(RuntimeException::class.java)

      val health = indexQueueHealth.health()

      assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_AVAILABLE.description)
    }
  }
  

  private fun mockHealthyQueue() {
    whenever(eventAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
    whenever(eventAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
    whenever(eventAmazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ())
    whenever(eventAmazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenReturn(someGetQueueAttributesResultForDLQ())
    whenever(indexAmazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
    whenever(indexAmazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
    whenever(indexAmazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ())
    whenever(indexAmazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenReturn(someGetQueueAttributesResultForDLQ())
  }

  private fun someGetQueueAttributesRequest() = GetQueueAttributesRequest(someQueueUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))
  private fun someGetQueueUrlResult(): GetQueueUrlResult = GetQueueUrlResult().withQueueUrl(someQueueUrl)
  private fun someGetQueueAttributesResultWithoutDLQ() = GetQueueAttributesResult().withAttributes(
      mapOf(MESSAGES_ON_QUEUE.awsName to someMessagesOnQueueCount.toString(),
          MESSAGES_IN_FLIGHT.awsName to someMessagesInFlightCount.toString()))

  private fun someGetQueueAttributesResultWithDLQ() = GetQueueAttributesResult().withAttributes(
      mapOf(MESSAGES_ON_QUEUE.awsName to someMessagesOnQueueCount.toString(),
          MESSAGES_IN_FLIGHT.awsName to someMessagesInFlightCount.toString(),
          QueueAttributeName.RedrivePolicy.toString() to "any redrive policy"))

  private fun someGetQueueAttributesRequestForDLQ() = GetQueueAttributesRequest(someDLQUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))
  private fun someGetQueueUrlResultForDLQ(): GetQueueUrlResult = GetQueueUrlResult().withQueueUrl(someDLQUrl)
  private fun someGetQueueAttributesResultForDLQ() = GetQueueAttributesResult().withAttributes(
      mapOf(MESSAGES_ON_QUEUE.awsName to someMessagesOnDLQCount.toString()))

}

