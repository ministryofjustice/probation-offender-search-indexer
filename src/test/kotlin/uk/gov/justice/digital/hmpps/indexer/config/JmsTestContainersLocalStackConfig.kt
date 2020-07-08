package uk.gov.justice.digital.hmpps.indexer.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.localstack.LocalStackContainer


@Configuration
@ConditionalOnProperty(name = ["aws.provider"], havingValue = "testcontainers-localstack")
class JmsTestContainersLocalStackConfig(private val localStackContainer: LocalStackContainer) {

  @Bean("eventAwsSqsClient")
  fun eventAwsSqsClient() = amazonSQS()

  @Bean
  fun eventAwsSqsDlqClient() = amazonSQS()

  @Bean
  fun indexAwsSqsClient() = amazonSQS()

  @Bean
  fun indexAwsSqsDlqClient() = amazonSQS()

  private fun amazonSQS(): AmazonSQS = AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localStackContainer.defaultCredentialsProvider)
      .build()
}
