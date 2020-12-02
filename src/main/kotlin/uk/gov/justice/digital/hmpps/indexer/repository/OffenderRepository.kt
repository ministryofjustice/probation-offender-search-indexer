package uk.gov.justice.digital.hmpps.indexer.repository

import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions.Type.ADD
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.ingest.PutPipelineRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.DeleteAliasRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.client.indices.PutMappingRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType.JSON
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.Offender

const val pipelineId = "pnc-pipeline"

@Repository
class OffenderRepository(@Qualifier("elasticSearchClient") private val client: RestHighLevelClient) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun save(offender: Offender, index: SyncIndex) {
    client.index(offender.toIndexRequest().index(index.indexName), RequestOptions.DEFAULT)
  }

  fun createIndex(index: SyncIndex) {
    log.info("creating index {}", index.indexName)
    client.indices().create(CreateIndexRequest(index.indexName), RequestOptions.DEFAULT)
    client.indices()
        .putMapping(PutMappingRequest(index.indexName).source("/es/mapping.json".resourceAsString(), JSON), RequestOptions.DEFAULT)
    client.ingest()
        .putPipeline(PutPipelineRequest(pipelineId, "/es/pipeline.json".resourceAsByteReference(), JSON), RequestOptions.DEFAULT)
  }

  fun deleteIndex(index: SyncIndex) {
    log.info("deleting index {}", index.indexName)
    if (client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(index.indexName), RequestOptions.DEFAULT)
    } else {
      log.warn("index {} was never there in the first place", index.indexName)
    }
  }

  fun doesIndexExist(index: SyncIndex): Boolean {
    return doesIndexExist(index.indexName)
  }

  fun doesIndexExist(index: String): Boolean {
    return client.indices().exists(GetIndexRequest(index), RequestOptions.DEFAULT)
  }

  fun switchAliasIndex(index: SyncIndex) {
    val alias = client.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT)
    client.indices()
        .updateAliases(IndicesAliasesRequest().addAliasAction(AliasActions(ADD).index(index.indexName).alias("offender")), RequestOptions.DEFAULT)

    alias.aliases[index.otherIndex().indexName]?.forEach {
      client.indices()
          .deleteAlias(DeleteAliasRequest(index.otherIndex().indexName, it.alias), RequestOptions.DEFAULT)
    }
  }

  fun offenderAliasIsPointingAt() : Set<String> {
    val alias = client.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT)
    return alias.aliases.keys
  }
}

private fun String.resourceAsString() = OffenderRepository::class.java.getResource(this).readText()
private fun String.resourceAsByteReference() = BytesArray(OffenderRepository::class.java.getResource(this).readBytes())
private fun Offender.toIndexRequest(): IndexRequest = IndexRequest()
    .setPipeline(pipelineId)
    .source(this.json, JSON)
    .id(this.crn)