package uk.gov.justice.digital.hmpps.indexer.repository

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.ingest.GetPipelineRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.client.indices.GetMappingsRequest
import org.elasticsearch.client.indices.GetMappingsResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN

internal class OffenderRepositoryIntegrationTest : IntegrationTest() {

  @Qualifier("elasticSearchClient")
  @Autowired
  lateinit var highLevelClient: RestHighLevelClient

  @Autowired
  lateinit var offenderRepository: OffenderRepository


  @Nested
  inner class CreateIndex {
    @Nested
    inner class NoIndexExists {
      @BeforeEach
      internal fun setUp() {
        highLevelClient.safeIndexDelete(BLUE.indexName)
        highLevelClient.safeIndexDelete(GREEN.indexName)
      }

      @Test
      fun `will create a brand new index`() {
        offenderRepository.createIndex(BLUE)

        assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
        assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()
      }

      @Test
      fun `can create either index`() {
        offenderRepository.createIndex(GREEN)

        assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
        assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()
      }
    }

    @Nested
    inner class OneIndexExists {
      @BeforeEach
      internal fun setUp() {
        highLevelClient.safeIndexDelete(BLUE.indexName)
        highLevelClient.safeIndexCreate(GREEN.indexName)
      }

      @Test
      fun `will create a brand new index`() {
        offenderRepository.createIndex(BLUE)

        assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
        assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()
      }

      @Test
      fun `cannot create an index that already exists`() {
        assertThatThrownBy { offenderRepository.createIndex(GREEN) }.hasMessageContaining("already exists")
      }
    }
  }

  @Nested
  inner class Mappings {
    lateinit var mappingProperties: Map<String, Any>
    @BeforeEach
    internal fun setUp() {
      highLevelClient.safeIndexDelete(BLUE.indexName)
      highLevelClient.safeIndexDelete(GREEN.indexName)

      offenderRepository.createIndex(BLUE)

      val mappings = highLevelClient.indices()
          .getMapping(GetMappingsRequest().indices(BLUE.indexName), RequestOptions.DEFAULT)
      mappingProperties = mappings.properties(BLUE.indexName)
    }

    @Test
    internal fun `mappings should be created with the index`() {
      assertThat(mappingProperties).isNotNull.hasSizeGreaterThan(1)
    }

    @Test
    internal fun `croNumberLowercase is a keyword`() {
      val value = mappingProperties.value("otherIds.properties.croNumberLowercase.type")
      assertThat(value).isEqualTo("keyword")
    }
    @Test
    internal fun `pncNumberLongYear is a keyword`() {
      val value = mappingProperties.value("otherIds.properties.pncNumberLongYear.type")
      assertThat(value).isEqualTo("keyword")
    }
    @Test
    internal fun `pncNumberShortYear is a keyword`() {
      val value = mappingProperties.value("otherIds.properties.pncNumberShortYear.type")
      assertThat(value).isEqualTo("keyword")
    }
    @Test
    internal fun `dateOfBirth is a date`() {
      val value = mappingProperties.value("dateOfBirth.type")
      assertThat(value).isEqualTo("date")
    }
    @Test
    internal fun `dateOfBirth allows multiple formats`() {
      val value = mappingProperties.value("dateOfBirth.format")
      assertThat(value).isEqualTo("yyyy-MM-dd||yyyy/MM/dd||dd-MM-yy||dd/MM/yy||dd-MM-yyyy||dd/MM/yyyy")
    }
    @Test
    internal fun `offenderManagers is nested`() {
      val value = mappingProperties.value("offenderManagers.type")
      assertThat(value).isEqualTo("nested")
    }
    @Test
    internal fun `the nested offenderManagers property probationArea code is a keyword`() {
      val value = mappingProperties.value("offenderManagers.properties.probationArea.properties.code.type")
      assertThat(value).isEqualTo("keyword")
    }
  }

  @Nested
  inner class Pipeline {
    @BeforeEach
    internal fun setUp() {
      highLevelClient.safeIndexDelete(BLUE.indexName)
      highLevelClient.safeIndexDelete(GREEN.indexName)
    }

    @Test
    internal fun `will have a pipeline created`() {
      offenderRepository.createIndex(BLUE)


      val response = highLevelClient.ingest().getPipeline(GetPipelineRequest("pnc-pipeline"), RequestOptions.DEFAULT)
      assertThat(response.isFound).isTrue()
    }

    @Test
    internal fun `can create both indexes even though there is a single shared pipeline`() {
      offenderRepository.createIndex(BLUE)
      offenderRepository.createIndex(GREEN)

      val response = highLevelClient.ingest().getPipeline(GetPipelineRequest("pnc-pipeline"), RequestOptions.DEFAULT)
      assertThat(response.isFound).isTrue()
    }
  }
}

fun RestHighLevelClient.safeIndexDelete(name: String) {
  if (this.indices().exists(GetIndexRequest(name), RequestOptions.DEFAULT)) {
    this.indices().delete(DeleteIndexRequest(name), RequestOptions.DEFAULT)
  }
}

fun RestHighLevelClient.safeIndexCreate(name: String) {
  if (this.indices().exists(GetIndexRequest(name), RequestOptions.DEFAULT).not()) {
    this.indices().create(CreateIndexRequest(name), RequestOptions.DEFAULT)
  }
}

fun GetMappingsResponse.properties(index: String): Map<String, Any> {
  val mapping = this.mappings()[index]?.sourceAsMap()!!
  val mappingProperties = mapping["properties"] as Map<*, *>
  @Suppress("UNCHECKED_CAST")
  return mappingProperties as Map<String, Any>
}

fun Map<*, *>.value(property: String) : String {
  val properties = property.split(".")
  var currentMap = this
  properties.forEach {
    when (val currentValue = currentMap[it]) {
      is Map<*, *> -> currentMap = currentValue
      is String -> return currentValue
    }
  }
  fail("$property not found")
}