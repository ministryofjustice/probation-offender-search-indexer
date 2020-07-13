package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import java.util.Optional

@Service
class IndexService(private val indexStatusRepository: IndexStatusRepository) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getCurrentIndex(): IndexStatus =
      indexStatusRepository.findById(INDEX_STATUS_ID).toNullable()
          ?: indexStatusRepository.save(IndexStatus(INDEX_STATUS_ID, SyncIndex.GREEN, null, null, false))

  fun buildIndex() = log.info("Received request to rebuild index")

  fun markIndexingComplete() = log.info("Received request to mark indexing complete")

  fun cancelIndexing() = log.info("Received request to cancel indexing")

  fun indexOffender(crn: String) = log.info("Received request to index offender {}", crn)
}

fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)