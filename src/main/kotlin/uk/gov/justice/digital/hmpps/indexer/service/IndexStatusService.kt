package uk.gov.justice.digital.hmpps.indexer.service

import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions
import org.elasticsearch.action.support.master.AcknowledgedResponse
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
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository, private val elasticSearchClient: RestHighLevelClient) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val indexName: String = (IndexStatus::class.annotations.find { it is Document } as? Document)?.indexName!!
  }

  fun initialiseIndexWhenRequired(): IndexStatusService {
    if (elasticSearchClient.indices().exists(GetIndexRequest(indexName), RequestOptions.DEFAULT).not()) {
      elasticSearchClient.indices().create(CreateIndexRequest(indexName), RequestOptions.DEFAULT)
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
      val newStatus = indexStatusRepository.save(currentIndexStatus.toBuildComplete().toSwitchIndex())
      elasticSearchClient.switchOffenderAlias(newStatus.currentIndex)
      return newStatus
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

fun RestHighLevelClient.switchOffenderAlias(index: SyncIndex): AcknowledgedResponse? = this.indices()
    .updateAliases(IndicesAliasesRequest().addAliasAction(AliasActions(AliasActions.Type.ADD).index(index.indexName).alias("offender")), RequestOptions.DEFAULT)