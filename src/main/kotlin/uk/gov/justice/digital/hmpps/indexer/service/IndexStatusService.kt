package uk.gov.justice.digital.hmpps.indexer.service

import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository, private val elasticSearchClient: RestHighLevelClient) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun initialiseIndexWhenRequired(): IndexStatusService {
    val document = IndexStatus::class.annotations.find { it is Document } as? Document
    if (elasticSearchClient.indices().exists(GetIndexRequest(document?.indexName!!), RequestOptions.DEFAULT).not()) {
      elasticSearchClient.indices().create(CreateIndexRequest(document.indexName), RequestOptions.DEFAULT)
      indexStatusRepository.save(IndexStatus.newIndex())
    }

    return this
  }

  fun getIndexStatus(): IndexStatus =
      indexStatusRepository.findById(INDEX_STATUS_ID).orElseThrow()

  fun markBuildInProgress(): IndexStatusService {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    }
    return this
  }

  fun markBuildCompleteAndSwitchIndex(): IndexStatusService {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildComplete().toSwitchIndex())
    }
    return this
  }

  fun markBuildCancelled() {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    }
  }
}
