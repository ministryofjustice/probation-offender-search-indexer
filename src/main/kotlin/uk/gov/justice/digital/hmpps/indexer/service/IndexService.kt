package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IndexService {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun buildIndex() = log.info("Received request to rebuild index")
}