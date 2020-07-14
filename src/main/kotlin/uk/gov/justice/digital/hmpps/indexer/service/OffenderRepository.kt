package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus

@Repository
class OffenderRepository {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun save(offender: Offender, indexStatus: IndexStatus) = log.info("would save an offender")

  @Suppress("unused")
  fun createIndex(indexStatus: IndexStatus) = log.info("would create a new index")
}
