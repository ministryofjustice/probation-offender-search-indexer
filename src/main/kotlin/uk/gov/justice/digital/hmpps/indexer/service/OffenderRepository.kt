package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Repository
class OffenderRepository {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun save(offender: Offender, index: SyncIndex) = log.info("would save an offender to {}", index)

  @Suppress("unused")
  fun createIndex(index: SyncIndex) = log.info("would create a new index {}", index)
}
