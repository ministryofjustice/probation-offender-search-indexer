package uk.gov.justice.digital.hmpps.indexer.repository

import org.elasticsearch.action.ingest.PutPipelineRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.PutMappingRequest
import org.elasticsearch.common.bytes.BytesArray
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.Offender

const val pipelineId = "pnc-pipeline"

@Repository
class OffenderRepository {
  @Qualifier("elasticSearchClient")
  @Autowired
  lateinit var highLevelClient: RestHighLevelClient

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  fun save(offender: Offender, index: SyncIndex) = log.info("would save an offender to {}", index)

  fun createIndex(index: SyncIndex): Unit {
    highLevelClient.indices().create(CreateIndexRequest(index.indexName), RequestOptions.DEFAULT)
    highLevelClient.indices()
        .putMapping(PutMappingRequest(index.indexName).source("/es/mapping.json".resourceAsString(), XContentType.JSON), RequestOptions.DEFAULT)
    highLevelClient.ingest()
        .putPipeline(PutPipelineRequest(pipelineId, "/es/pipeline.json".resourceAsByteReference(), XContentType.JSON), RequestOptions.DEFAULT)
  }
}

private fun String.resourceAsString() = OffenderRepository::class.java.getResource(this).readText()
private fun String.resourceAsByteReference() = BytesArray(OffenderRepository::class.java.getResource(this).readBytes())