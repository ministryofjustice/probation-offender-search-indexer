package uk.gov.justice.digital.hmpps.indexer.config

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
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
  fun indexAwsSqsAsyncClient() = amazonSQSAsync()

  @Bean
  fun indexAwsSqsDlqClient() = amazonSQS()

  @Bean
  fun indexAwsSqsDlqAsyncClient() = amazonSQSAsync()

  private fun amazonSQS(): AmazonSQS = AmazonSQSClientBuilder.standard()
    .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
    .withCredentials(localStackContainer.defaultCredentialsProvider)
    .build()

  private fun amazonSQSAsync(): AmazonSQSAsync = AmazonSQSAsyncClientBuilder.standard()
    .withEndpointConfiguration(localStackContainer.getEndpointConfiguration(LocalStackContainer.Service.SQS))
    .withCredentials(localStackContainer.defaultCredentialsProvider)
    .build()
}
