package uk.gov.justice.digital.hmpps.indexer.listeners

import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.service.IndexService

class EventListenerTest {
  private val indexService = mock<IndexService>()
  private val eventListener = EventListener(Gson(), indexService)

  @Test
  fun `Calls offender synchroniser service`() {
    eventListener.processOffenderEvent(
      """
      {  
        "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
        "Message": "{\"offenderId\":490001467,\"crn\":\"X123456\",\"nomsNumber\":\"A1234BC\"}",
        "MessageAttributes": {
          "eventType": {
            "Type": "String",
            "Value": "OFFENDER_CHANGED"
          }
        }
      }
      """.trimIndent()
    )

    verify(indexService).updateOffender("X123456")
  }
}
