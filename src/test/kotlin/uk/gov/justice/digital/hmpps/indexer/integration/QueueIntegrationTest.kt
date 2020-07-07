package uk.gov.justice.digital.hmpps.indexer.integration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles


@ActiveProfiles(profiles = ["test"])
abstract class QueueIntegrationTest : IntegrationTest() {

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

fun String.readResourceAsText(): String = QueueIntegrationTest::class.java.getResource(this).readText()