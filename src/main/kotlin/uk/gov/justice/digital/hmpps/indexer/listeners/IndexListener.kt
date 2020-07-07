package uk.gov.justice.digital.hmpps.indexer.listeners

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service

@Service
class IndexListener(
    @Qualifier("gson") private val gson: com.google.gson.Gson
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${index.sqs.queue.name}", containerFactory = "jmsIndexListenerContainerFactory")
  fun processIndexRequest(requestJson: String?, msg: javax.jms.Message) {
    log.debug(requestJson)
    val indexRequest = gson.fromJson(requestJson, IndexRequest::class.java)
    log.info("Received message request {}", indexRequest)
  }
}

data class IndexRequest(
    val requestType: IndexRequestType?,
    val prisonerNumber: String? = null
)

enum class IndexRequestType {
  REBUILD, OFFENDER_LIST, OFFENDER
}