package uk.gov.justice.digital.hmpps.indexer.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["aws.provider"], havingValue = "localstack")
class JmsLocalStackConfig {
  @Bean
  fun eventAwsSqsClient(@Value("\${event.sqs.endpoint.url}") serviceEndpoint: String,
                        @Value("\${event.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(serviceEndpoint, region)

  @Bean
  fun eventAwsSqsDlqClient(@Value("\${event.sqs.endpoint.url}") serviceEndpoint: String,
                           @Value("\${event.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(serviceEndpoint, region)


  @Bean
  fun indexAwsSqsClient(@Value("\${index.sqs.endpoint.url}") serviceEndpoint: String,
                        @Value("\${index.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(serviceEndpoint, region)

  @Bean
  fun indexAwsSqsDlqClient(@Value("\${index.sqs.endpoint.url}") serviceEndpoint: String,
                           @Value("\${index.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(serviceEndpoint, region)

  private fun amazonSQS(serviceEndpoint: String, region: String): AmazonSQS =
    AmazonSQSClientBuilder.standard()
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
        .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
        .build()

}
