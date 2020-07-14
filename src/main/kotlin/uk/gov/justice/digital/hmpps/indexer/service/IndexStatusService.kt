package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import java.time.LocalDateTime
import java.util.Optional

class IndexStatusService(private val indexStatusRepository: IndexStatusRepository) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getOrCreateCurrentIndex(): IndexStatus =
      indexStatusRepository.findById(INDEX_STATUS_ID).toNullable()
          ?: indexStatusRepository.save(IndexStatus(INDEX_STATUS_ID, SyncIndex.GREEN, null, null, false))

  fun markRebuildInProgress(): Boolean =
    if (getOrCreateCurrentIndex().inProgress) {
      false
    } else {
      indexStatusRepository.save(IndexStatus(INDEX_STATUS_ID, SyncIndex.GREEN, LocalDateTime.now(), null, true))
      true
    }

}

private fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)