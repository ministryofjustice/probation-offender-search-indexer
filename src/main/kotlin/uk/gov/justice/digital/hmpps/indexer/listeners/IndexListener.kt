package uk.gov.justice.digital.hmpps.indexer.listeners

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.OFFENDER
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_OFFENDER_PAGE
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexRequestType.POPULATE_INDEX
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.OffenderPage

@Service
class IndexListener(
    @Qualifier("gson") private val gson: com.google.gson.Gson,
    private val indexService: IndexService

) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${index.sqs.queue.name}", containerFactory = "jmsIndexListenerContainerFactory")
  fun processIndexRequest(requestJson: String?, msg: javax.jms.Message) {
    log.debug(requestJson)
    val indexRequest = gson.fromJson(requestJson, IndexMessageRequest::class.java)
    log.info("Received message request {}", indexRequest)
    when(indexRequest.type) {
      POPULATE_INDEX -> indexService.populateIndex(indexRequest.index!!)
      POPULATE_OFFENDER_PAGE -> indexService.populateIndexWithOffenderPage(indexRequest.offenderPage!!)
      OFFENDER -> TODO()
    }
  }
}

data class IndexMessageRequest(
    val type: IndexRequestType,
    val index: SyncIndex? = null,
    val offenderPage: OffenderPage? = null
)

enum class IndexRequestType {
  POPULATE_INDEX, POPULATE_OFFENDER_PAGE, OFFENDER
}