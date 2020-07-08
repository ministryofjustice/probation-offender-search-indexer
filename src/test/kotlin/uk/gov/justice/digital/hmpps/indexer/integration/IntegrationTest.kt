package uk.gov.justice.digital.hmpps.indexer.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.google.gson.Gson
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.indexer.health.wiremock.CommunityMockServer
import uk.gov.justice.digital.hmpps.indexer.health.wiremock.OAuthMockServer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["test"])
abstract class IntegrationTest {

  companion object {
    internal val oauthMockServer = OAuthMockServer()
    internal val communityMockServer = CommunityMockServer()

    @Suppress("unused")
    @BeforeAll
    @JvmStatic
    fun startMocks() {
      oauthMockServer.start()
      communityMockServer.start()
    }

    @Suppress("unused")
    @AfterAll
    @JvmStatic
    fun stopMocks() {
      oauthMockServer.stop()
      communityMockServer.stop()
    }
  }


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
  private lateinit var gson: Gson

}
