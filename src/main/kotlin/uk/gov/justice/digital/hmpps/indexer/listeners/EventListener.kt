package uk.gov.justice.digital.hmpps.indexer.listeners

import com.google.gson.Gson
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service


@Service
class EventListener(
  @Qualifier("gson") private val gson: Gson
) {
  companion object : Logging

  @JmsListener(destination = "\${event.sqs.queue.name}", containerFactory = "jmsListenerContainerFactory")
  fun processOffenderEvent(requestJson: String?) {
      val ( _, messageId, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
      val eventType = messageAttributes.eventType.Value
      logger.debug {"Received message $messageId type $eventType"}


      when (eventType) {
        else -> logger.warn {"We received a message of event type $eventType which I really wasn't expecting"}
      }

    }
  }

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
