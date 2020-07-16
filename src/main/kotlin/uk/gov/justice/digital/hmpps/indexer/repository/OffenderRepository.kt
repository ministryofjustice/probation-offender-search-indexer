package uk.gov.justice.digital.hmpps.indexer.repository

import org.apache.logging.log4j.kotlin.Logging
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.ingest.PutPipelineRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.client.indices.PutMappingRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType.JSON
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.Offender

const val pipelineId = "pnc-pipeline"

@Repository
class OffenderRepository(@Qualifier("elasticSearchClient") private val client: RestHighLevelClient) {
  companion object : Logging

  fun save(offender: Offender, index: SyncIndex) {
    client.index(offender.toIndexRequest().index(index.indexName), RequestOptions.DEFAULT)
  }

  fun createIndex(index: SyncIndex) {
    logger.info { "creating index ${index.indexName}" }
    client.indices().create(CreateIndexRequest(index.indexName), RequestOptions.DEFAULT)
    client.indices()
        .putMapping(PutMappingRequest(index.indexName).source("/es/mapping.json".resourceAsString(), JSON), RequestOptions.DEFAULT)
    client.ingest()
        .putPipeline(PutPipelineRequest(pipelineId, "/es/pipeline.json".resourceAsByteReference(), JSON), RequestOptions.DEFAULT)
  }

  fun deleteIndex(index: SyncIndex) {
    logger.info { "deleting index ${index.indexName}"  }
    if (client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(index.indexName), RequestOptions.DEFAULT)
    } else {
      logger.warn { "index ${index.indexName} was never there in the first place" }
    }
  }
  fun doesIndexExist(index: SyncIndex) : Boolean{
    logger.info { "deleting index ${index.indexName}" }
    return client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)
  }
}

private fun String.resourceAsString() = OffenderRepository::class.java.getResource(this).readText()
private fun String.resourceAsByteReference() = BytesArray(OffenderRepository::class.java.getResource(this).readBytes())
private fun Offender.toIndexRequest(): IndexRequest = IndexRequest()
    .setPipeline(pipelineId)
    .source(this.body, JSON)
    .id(this.crn)