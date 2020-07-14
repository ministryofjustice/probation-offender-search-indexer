package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import java.util.Optional

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getOrCreateCurrentIndexStatus(): IndexStatus =
      indexStatusRepository.findById(INDEX_STATUS_ID).toNullable()
          ?: indexStatusRepository.save(IndexStatus.newIndex())

  fun markBuildInProgress() {
    val currentIndexStatus = getOrCreateCurrentIndexStatus()
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    }
  }

  fun markBuildComplete() {
    val currentIndexStatus = getOrCreateCurrentIndexStatus()
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildComplete())
    }
  }

  fun markBuildCancelled() {
    val currentIndexStatus = getOrCreateCurrentIndexStatus()
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    }
  }
}

private fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)