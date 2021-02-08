package uk.gov.justice.digital.hmpps.indexer.integration.repository

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.get.GetRequest
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
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository
import uk.gov.justice.digital.hmpps.indexer.service.IDs
import uk.gov.justice.digital.hmpps.indexer.service.Offender
import uk.gov.justice.digital.hmpps.indexer.service.OffenderDetail

internal class OffenderRepositoryTest : IntegrationTestBase() {

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
        deleteOffenderIndexes()
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
        deleteOffenderIndexes()
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

    @Nested
    inner class Mappings {
      lateinit var mappingProperties: Map<String, Any>
      @BeforeEach
      internal fun setUp() {
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
        deleteOffenderIndexes()
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

  @Nested
  inner class Save {

    @Test
    internal fun `will save offender in the correct index`() {
      offenderRepository.save(Offender(OffenderDetail(otherIds = IDs(crn = "X12345"), offenderId = 99).asJson(), null), BLUE)

      assertThat(highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).isExists).isTrue()
      assertThat(highLevelClient.get(GetRequest(GREEN.indexName).id("X12345"), RequestOptions.DEFAULT).isExists).isFalse()
    }

    @Test
    internal fun `will save json`() {
      offenderRepository.save(Offender(OffenderDetail(otherIds = IDs(crn = "X12345"), offenderId = 99).asJson(), null), BLUE)

      val json = highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).sourceAsString

      assertThatJson(json).node("offenderId").isEqualTo(99)
      assertThatJson(json).node("otherIds.crn").isEqualTo("X12345")
      assertThatJson(json).node("mappa").isNull()
      val offenderDetail = gson.fromJson(json, OffenderDetail::class.java)
      assertThat(offenderDetail.otherIds.crn).isEqualTo("X12345")
    }

    @Test
    internal fun `will save two canonical forms of pncNumber in pncNumberLongYear and pncNumberShortYear`() {
      offenderRepository.save(Offender(OffenderDetail(otherIds = IDs(crn = "X12345", pncNumber = "2016/01234Z"), offenderId = 99).asJson(), null), BLUE)

      val json = highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).sourceAsString

      assertThatJson(json).node("otherIds.pncNumber").isEqualTo("\"2016/01234Z\"")
      assertThatJson(json).node("otherIds.pncNumberLongYear").isEqualTo("\"2016/1234z\"")
      assertThatJson(json).node("otherIds.pncNumberShortYear").isEqualTo("\"16/1234z\"")
    }

    @Test
    internal fun `will save lowercase version of croNumber`() {
      offenderRepository.save(Offender(OffenderDetail(offenderId = 99, otherIds = IDs(crn = "X12345", croNumber = "16/01234Z")).asJson(), null), BLUE)

      val json = highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).sourceAsString

      assertThatJson(json).node("otherIds.croNumber").isEqualTo("\"16/01234Z\"")
      assertThatJson(json).node("otherIds.croNumberLowercase").isEqualTo("\"16/01234z\"")
    }

    @Test
    internal fun `will happily ignore missing pnc and cro numbers`() {
      offenderRepository.save(Offender(OffenderDetail(offenderId = 99, otherIds = IDs(crn = "X12345", croNumber = null, pncNumber = null)).asJson(), null), BLUE)

      val json = highLevelClient.get(GetRequest(BLUE.indexName).id("X12345"), RequestOptions.DEFAULT).sourceAsString

      assertThatJson(json).isObject.doesNotContainKey("otherIds.croNumber")
      assertThatJson(json).isObject.doesNotContainKey("otherIds.pncNumber")
    }
  }

  @Nested
  inner class DeleteIndex {
    @BeforeEach
    internal fun setUp() {
      deleteOffenderIndexes()
    }

    @Test
    internal fun `will delete an existing index`() {
      offenderRepository.createIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()

      offenderRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isFalse()
    }

    @Test
    internal fun `will leave the other index alone`() {
      offenderRepository.createIndex(BLUE)
      offenderRepository.createIndex(GREEN)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isTrue()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()

      offenderRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
      assertThat(highLevelClient.indices().exists(GetIndexRequest(GREEN.indexName), RequestOptions.DEFAULT)).isTrue()
    }

    @Test
    internal fun `will not complain if index to delete does not exist`() {
      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()

      offenderRepository.deleteIndex(BLUE)

      assertThat(highLevelClient.indices().exists(GetIndexRequest(BLUE.indexName), RequestOptions.DEFAULT)).isFalse()
    }
  }

  @Nested
  inner class DoesIndexExist {
    @BeforeEach
    internal fun setUp() {
      deleteOffenderIndexes()
    }

    @Test
    internal fun `will report true when index exists`() {
      offenderRepository.createIndex(BLUE)
      assertThat(offenderRepository.doesIndexExist(BLUE)).isTrue()
      assertThat(offenderRepository.doesIndexExist(GREEN)).isFalse()
    }
    @Test
    internal fun `will report false when index does not exists`() {
      assertThat(offenderRepository.doesIndexExist(BLUE)).isFalse()
      assertThat(offenderRepository.doesIndexExist(GREEN)).isFalse()
    }
  }

  @Nested
  inner class SwitchAliasIndex {

    @Nested
    inner class BeforeAliasExists {
      @Test
      fun `can create an alias for active index`() {
        offenderRepository.switchAliasIndex(GREEN)
        assertThat(highLevelClient.indices().exists(GetIndexRequest("offender"), RequestOptions.DEFAULT)).isTrue()
        assertThat(highLevelClient.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT).aliases).containsKey(GREEN.indexName)
        assertThat(highLevelClient.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT).aliases).doesNotContainKey(BLUE.indexName)
      }

      @Test
      fun `alias is pointing at no index`() {
        val indexes = offenderRepository.offenderAliasIsPointingAt()

        assertThat(indexes).isEmpty()
      }
    }

    @Nested
    inner class WhenAliasExists {
      @BeforeEach
      internal fun setUp() {
        offenderRepository.switchAliasIndex(GREEN)
      }

      @Test
      fun `can switch an alias for active index`() {
        offenderRepository.switchAliasIndex(BLUE)
        assertThat(highLevelClient.indices().exists(GetIndexRequest("offender"), RequestOptions.DEFAULT)).isTrue()
        assertThat(highLevelClient.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT).aliases).containsKey(BLUE.indexName)
        assertThat(highLevelClient.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT).aliases).doesNotContainKey(GREEN.indexName)
      }

      @Test
      fun `alias is pointing at active index`() {
        offenderRepository.switchAliasIndex(BLUE)
        val indexes = offenderRepository.offenderAliasIsPointingAt()

        assertThat(indexes).containsExactly(BLUE.indexName)
      }
    }

    @Nested
    inner class WhenAliasExistsOnCorrectIndex {
      @BeforeEach
      internal fun setUp() {
        offenderRepository.switchAliasIndex(BLUE)
      }

      @Test
      fun `will keep an alias for active index`() {
        offenderRepository.switchAliasIndex(BLUE)
        assertThat(highLevelClient.indices().exists(GetIndexRequest("offender"), RequestOptions.DEFAULT)).isTrue()
        assertThat(highLevelClient.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT).aliases).containsKey(BLUE.indexName)
        assertThat(highLevelClient.indices().getAlias(GetAliasesRequest().aliases("offender"), RequestOptions.DEFAULT).aliases).doesNotContainKey(GREEN.indexName)
      }
    }
  }

  private fun Any.asJson() = gson.toJson(this)
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

fun Map<*, *>.value(property: String): String {
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
