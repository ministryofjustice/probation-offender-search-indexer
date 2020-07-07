package uk.gov.justice.digital.hmpps.indexer.config

import com.amazonaws.services.sqs.AmazonSQS
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
                    @Value("\${event.sqs.queue.name}") eventQueueName: String): String {
    return eventAwsSqsClient.getQueueUrl(eventQueueName).queueUrl
  }

  @Bean("indexQueueUrl")
  fun indexQueueUrl(@Qualifier("indexAwsSqsClient") indexAwsSqsClient: AmazonSQS,
                    @Value("\${index.sqs.queue.name}") indexQueueName: String): String {
    return indexAwsSqsClient.getQueueUrl(indexQueueName).queueUrl
  }
}
