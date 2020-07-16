package uk.gov.justice.digital.hmpps.indexer.config

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.services.sqs.AmazonSQS
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.destination.DynamicDestinationResolver
import javax.jms.Session

@Configuration
@EnableJms
class JmsListenerConfig {
  companion object : Logging

  @Bean
  fun jmsListenerContainerFactory(@Qualifier("eventAwsSqsClient") eventAwsSqsClient: AmazonSQS)  = defaultJmsListenerContainerFactory(eventAwsSqsClient)

  @Bean
  fun jmsIndexListenerContainerFactory(@Qualifier("indexAwsSqsClient") indexAwsSqsClient: AmazonSQS)  = defaultJmsListenerContainerFactory(indexAwsSqsClient)

  private fun defaultJmsListenerContainerFactory(awsSqsClient: AmazonSQS): DefaultJmsListenerContainerFactory  {
    val factory = DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(SQSConnectionFactory(ProviderConfiguration(), awsSqsClient))
    factory.setDestinationResolver(DynamicDestinationResolver())
    factory.setConcurrency("1-1")
    factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    factory.setErrorHandler { t: Throwable -> logger.error(t) {"Error caught in jms listener"} }
    return factory
  }
}
