package uk.gov.justice.digital.hmpps.indexer.integration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.amazonaws.services.sqs.AmazonSQS
import com.google.gson.Gson
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.indexer.integration.helpers.JwtAuthHelper
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.OAuthExtension
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.repository.IndexStatusRepository
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.IndexStatusService

@ExtendWith(OAuthExtension::class, CommunityApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["test"])
abstract class IntegrationTestBase {

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

  @Autowired
  internal lateinit var gson: Gson

  @Autowired
  lateinit var elasticSearchClient: RestHighLevelClient

  @Autowired
  lateinit var offenderRespository: OffenderRepository

  @Autowired
  lateinit var indexStatusRepository: IndexStatusRepository

  @Autowired
  lateinit var indexStatusService: IndexStatusService

  @LocalServerPort
  protected var port: Int = 0
  @Autowired
  lateinit var eventQueueUrl: String
  @Autowired
  lateinit var indexQueueUrl: String

  internal fun setAuthorisation(
      user: String = "probation-offender-search-indexer-client",
      roles: List<String> = listOf()
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles)

  fun createOffenderIndexes() {
    SyncIndex.values().map { offenderRespository.createIndex(it) }
  }

  fun deleteOffenderIndexes() {
    SyncIndex.values().map { offenderRespository.deleteIndex(it) }
  }

  fun initialiseIndexStatus() {
    indexStatusRepository.deleteAll()
    indexStatusRepository.save(IndexStatus.newIndex())
  }

  fun buildInitialIndex() {
    indexService.prepareIndexForRebuild()
    indexService.markIndexingComplete()
  }

  fun getIndexCount(index: SyncIndex): Long {
    return getIndexCount(index.indexName)
  }

  fun getIndexCount(index: String): Long = elasticSearchClient.count(CountRequest(index), RequestOptions.DEFAULT).count


  fun searchByCrn(crn: String, index: SyncIndex = GREEN): SearchHits {
    val query = QueryBuilders.matchQuery("otherIds.crn", crn)
    return search(query)
  }

  fun search(query: QueryBuilder, index: SyncIndex = GREEN): SearchHits {
    val search = SearchSourceBuilder().apply { query(query) }
    val request = SearchRequest(arrayOf(index.indexName), search)
    return elasticSearchClient.search(request, RequestOptions.DEFAULT).hits
  }

  fun getById(index: String, crn: String): String {
    return elasticSearchClient.get(GetRequest(index).id(crn), RequestOptions.DEFAULT).sourceAsString
  }

  fun findLogAppender(javaClass: Class<IndexService>): ListAppender<ILoggingEvent> {
    val indexServiceLogger = LoggerFactory.getLogger(javaClass) as Logger
    val listAppender = ListAppender<ILoggingEvent>()
    listAppender.start()
    indexServiceLogger.addAppender(listAppender)
    return listAppender
  }

  infix fun List<ILoggingEvent>?.hasLogMessageContaining(partialMessage: String) =
      this?.find { logEvent -> logEvent.message.contains(partialMessage) } != null

  fun getNumberOfMessagesCurrentlyOnEventQueue(): Int? {
    val queueAttributes = eventAwsSqsClient.getQueueAttributes(eventQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun getNumberOfMessagesCurrentlyOnIndexQueue(): Int? {
    val queueAttributes = indexAwsSqsClient.getQueueAttributes(indexQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  fun String.readResourceAsText(): String = this::class.java.getResource(this).readText()
}
