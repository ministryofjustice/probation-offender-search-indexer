package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SearchService {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun match(): List<String> {
    log.info("Received request to match offender")
    return listOf()
  }
}