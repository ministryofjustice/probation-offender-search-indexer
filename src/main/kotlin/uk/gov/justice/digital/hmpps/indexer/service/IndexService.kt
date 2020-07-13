package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IndexService {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun buildIndex() = log.info("Received request to rebuild index")

  fun markIndexingComplete() = log.info("Received request to mark indexing complete")

  fun cancelIndexing() = log.info("Received request to cancel indexing")

  fun indexOffender(crn: String) = log.info("Received request to index offender {}", crn)
}