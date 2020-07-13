package uk.gov.justice.digital.hmpps.indexer.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.google.gson.Gson
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.OAuthExtension

@ExtendWith(OAuthExtension::class, CommunityApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["test"])
abstract class IntegrationTest {

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
