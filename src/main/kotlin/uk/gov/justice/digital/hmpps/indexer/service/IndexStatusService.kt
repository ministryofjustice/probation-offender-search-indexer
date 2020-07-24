package uk.gov.justice.digital.hmpps.indexer.service

import org.elasticsearch.client.RestHighLevelClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.integration.repository.IndexStatusRepository

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository, private val elasticSearchClient: RestHighLevelClient) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun initialiseIndexWhenRequired(): IndexStatusService {
      if (!indexStatusRepository.existsById("STATUS")) {
        indexStatusRepository.save(IndexStatus.newIndex())
      }
    return this
  }

  fun getIndexStatus(): IndexStatus =
      indexStatusRepository.findById(INDEX_STATUS_ID).orElseThrow()

  fun markBuildInProgress(): IndexStatus {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress().not()) {
      return indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    }
    return currentIndexStatus
  }

  fun markBuildCompleteAndSwitchIndex(): IndexStatus {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      return indexStatusRepository.save(currentIndexStatus.toBuildComplete().toSwitchIndex())
    }
    return currentIndexStatus
  }

  fun markBuildCancelled(): IndexStatus {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      return indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    }
    return currentIndexStatus
  }
}