package uk.gov.justice.digital.hmpps.indexer.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.google.gson.Gson
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.OAuthExtension
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.IndexStatusService
import java.time.Duration

@ExtendWith(OAuthExtension::class, CommunityApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["test"])
abstract class IntegrationTest {

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  protected lateinit var indexService: IndexService

  @SpyBean
  @Qualifier("eventAwsSqsClient")
  internal lateinit var eventAwsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("indexAwsSqsClient")
  internal lateinit var indexAwsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("eventAwsSqsDlqClient")
  internal lateinit var eventAwsSqsDlqClient: AmazonSQS

  @SpyBean
  @Qualifier("indexAwsSqsDlqClient")
  internal lateinit var indexAwsSqsDlqClient: AmazonSQS

  @Autowired
  internal lateinit var gson: Gson

  @Autowired
  lateinit var elasticSearchClient: RestHighLevelClient

  @Autowired
  lateinit var elasticsearchOperations: ElasticsearchOperations

  @Autowired
  lateinit var offenderRespository: OffenderRepository

  @Autowired
  lateinit var indexStatusService: IndexStatusService

  @LocalServerPort
  protected var port: Int = 0

  fun setupIndexes() {
    createIndexStatusIndex()
    createOffenderIndexes()
  }

  fun tearDownIndexes() {
    deleteIndexStatusIndex()
    deleteOffenderIndexes()
  }

  internal fun setAuthorisation(user: String = "probation-offender-search-indexer-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit {
    val token = jwtAuthHelper.createJwt(subject = user,
        scope = listOf("read"),
        expiryTime = Duration.ofHours(1L),
        roles = roles)
    return { it.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
  }

  private fun createIndexStatusIndex() {
    indexStatusService
        .initialiseIndexWhenRequired()
        .markBuildInProgress()
        .markBuildCompleteAndSwitchIndex()
  }

  private fun deleteIndexStatusIndex() {
    elasticSearchClient.indices().delete(DeleteIndexRequest("offender-index-status"), RequestOptions.DEFAULT)
  }

  private fun createOffenderIndexes() {
    SyncIndex.values().map { offenderRespository.createIndex(it) }
  }

  private fun deleteOffenderIndexes() {
    SyncIndex.values().map { offenderRespository.deleteIndex(it) }
  }

  fun search(crn: String): SearchResponse {
    val query = QueryBuilders.matchQuery("otherIds.crn", crn)
    val search = SearchSourceBuilder().apply { query(query) }
    val request = SearchRequest(arrayOf(SyncIndex.GREEN.indexName), search)
    return elasticSearchClient.search(request, RequestOptions.DEFAULT)
  }
}
