package uk.gov.justice.digital.hmpps.indexer.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms

@Configuration
@EnableJms
class TestJmsConfig {
  @Bean("eventQueueUrl")
  fun eventQueueUrl(@Qualifier("eventAwsSqsClient") eventAwsSqsClient: AmazonSQS,
                    @Value("\${event.sqs.queue.name}") eventQueueName: String,
                    @Value("\${event.sqs.dlq.name}") eventDlqQueueName: String): String {
    return queueUrl(eventAwsSqsClient, eventQueueName, eventDlqQueueName)
  }

  @Bean("indexQueueUrl")
  fun indexQueueUrl(@Qualifier("indexAwsSqsClient") indexAwsSqsClient: AmazonSQS,
                    @Value("\${index.sqs.queue.name}") indexQueueName: String,
                    @Value("\${index.sqs.dlq.name}") indexDlqQueueName: String): String {
    return queueUrl(indexAwsSqsClient, indexQueueName, indexDlqQueueName)
  }

  @Bean("indexDlqUrl")
  fun indexDlqUrl(@Qualifier("indexAwsSqsClient") indexAwsSqsClient: AmazonSQS,
                  @Value("\${index.sqs.dlq.name}") indexDlqQueueName: String) : String = indexAwsSqsClient.getQueueUrl(indexDlqQueueName).queueUrl

  @Bean("eventDlqUrl")
  fun eventDlqUrl(@Qualifier("eventAwsSqsClient") eventAwsSqsClient: AmazonSQS,
                  @Value("\${event.sqs.dlq.name}") eventDlqQueueName: String) : String = eventAwsSqsClient.getQueueUrl(eventDlqQueueName).queueUrl

  private fun queueUrl(awsSqsClient: AmazonSQS, queueName: String, dlqName: String): String {
    val queueUrl = awsSqsClient.getQueueUrl(queueName).queueUrl
    val dlqUrl = awsSqsClient.getQueueUrl(dlqName).queueUrl
    // This is necessary due to a bug in localstack when running in testcontainers that the redrive policy gets lost
    val dlqArn = awsSqsClient.getQueueAttributes(dlqUrl, listOf(QueueAttributeName.QueueArn.toString()))

    // the queue should already be created by the setup script - but should reset set the redrive policy
    awsSqsClient.createQueue(CreateQueueRequest(queueName).withAttributes(
        mapOf(QueueAttributeName.RedrivePolicy.toString() to
            """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"5"}""")
    ))

    return queueUrl
  }
}
