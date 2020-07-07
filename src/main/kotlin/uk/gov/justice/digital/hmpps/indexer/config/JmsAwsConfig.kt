package uk.gov.justice.digital.hmpps.indexer.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["aws.provider"], havingValue = "aws")
class JmsAwsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean("eventAwsSqsClient")
  fun eventAwsSqsClient(@Value("\${event.sqs.aws.access.key.id}") accessKey: String,
                   @Value("\${event.sqs.aws.secret.access.key}") secretKey: String,
                   @Value("\${event.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(accessKey, secretKey, region)

  @Bean
  fun eventAwsSqsDlqClient(@Value("\${event.sqs.aws.dlq.access.key.id}") accessKey: String,
                      @Value("\${event.sqs.aws.dlq.secret.access.key}") secretKey: String,
                      @Value("\${event.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(accessKey, secretKey, region)


  @Bean("indexAwsSqsClient")
  fun indexAwsSqsClient(@Value("\${index.sqs.aws.access.key.id}") accessKey: String,
                        @Value("\${index.sqs.aws.secret.access.key}") secretKey: String,
                        @Value("\${index.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(accessKey, secretKey, region)

  @Bean
  fun indexAwsSqsDlqClient(@Value("\${index.sqs.aws.dlq.access.key.id}") accessKey: String,
                           @Value("\${index.sqs.aws.dlq.secret.access.key}") secretKey: String,
                           @Value("\${index.sqs.endpoint.region}") region: String): AmazonSQS =
      amazonSQS(accessKey, secretKey, region)

  private fun amazonSQS(accessKey: String, secretKey: String, region: String): AmazonSQS {
    return AmazonSQSClientBuilder.standard()
        .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
        .withRegion(region)
        .build()
  }

}
