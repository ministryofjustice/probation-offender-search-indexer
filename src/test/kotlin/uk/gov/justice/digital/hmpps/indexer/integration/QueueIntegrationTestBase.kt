package uk.gov.justice.digital.hmpps.indexer.integration

import org.springframework.beans.factory.annotation.Autowired


abstract class QueueIntegrationTestBase : IntegrationTestBase() {

  @Autowired
  lateinit var eventQueueUrl: String

  @Autowired
  lateinit var indexQueueUrl: String


  fun getNumberOfMessagesCurrentlyOnEventQueue(): Int? {
    val queueAttributes = eventAwsSqsClient.getQueueAttributes(eventQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int? {
    val queueAttributes = indexAwsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }


}

fun String.readResourceAsText(): String = QueueIntegrationTestBase::class.java.getResource(this).readText()