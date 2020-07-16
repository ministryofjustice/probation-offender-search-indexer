package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import java.util.Optional

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  /*
   * The side effect (creating the index state if it is doesn't exist) probably means the method name should be getOrCreateIndexStatus.
   * However I tried that and found it confuses the code as the index status exists 99% of the time.  So I'm keeping the side effect
   * and the iffy name for convenience.
   */
  fun getIndexStatus(): IndexStatus =
      indexStatusRepository.findById(INDEX_STATUS_ID).toNullable()
          ?: indexStatusRepository.save(IndexStatus.newIndex())

  fun markBuildInProgress() {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    }
  }

  fun markBuildCompleteAndSwitchIndex() {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildComplete().toSwitchIndex())
    }
  }

  fun markBuildCancelled() {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    }
  }
}

private fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)