package uk.gov.justice.digital.hmpps.indexer.listeners

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service


@Service
class EventListener(
  @Qualifier("gson") private val gson: Gson
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${event.sqs.queue.name}", containerFactory = "jmsListenerContainerFactory")
  fun processOffenderEvent(requestJson: String?) {
      val ( _, messageId, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
      val eventType = messageAttributes.eventType.Value
      log.debug("Received message {} type {}", messageId, eventType)


      when (eventType) {
        else -> log.warn("We received a message of event type {} which I really wasn't expecting", eventType)
      }

    }
  }

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
