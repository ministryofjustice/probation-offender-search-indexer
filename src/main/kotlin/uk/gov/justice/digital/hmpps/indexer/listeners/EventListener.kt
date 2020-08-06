package uk.gov.justice.digital.hmpps.indexer.listeners

import com.google.gson.Gson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.service.IndexService


@Service
class EventListener(
    @Qualifier("gson") private val gson: Gson,
    private val indexService: IndexService
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${event.sqs.queue.name}", containerFactory = "jmsListenerContainerFactory")
  fun processOffenderEvent(requestJson: String?) {
    val (message, _, messageAttributes) = gson.fromJson(requestJson, Message::class.java)

    when (val eventType = messageAttributes.eventType.Value) {
      "OFFENDER_CHANGED" -> indexService.updateOffender(gson.fromJson(message, OffenderChangedEvent::class.java).crn)
      else -> log.error("We received a message of event type {} which I really wasn't expecting", eventType)
    }

  }
}

data class EventType(val Value: String)
data class MessageAttributes(val eventType: EventType)
data class Message(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
data class OffenderChangedEvent(val crn: String)