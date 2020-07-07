package uk.gov.justice.digital.hmpps.indexer.integration

import com.amazonaws.services.sqs.AmazonSQS
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["test"])
abstract class IntegrationTest {

  @SpyBean
  @Qualifier("eventAwsSqsClient")
  internal lateinit var eventAwsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("indexAwsSqsClient")
  internal lateinit var indexAwsSqsClient: AmazonSQS

  @Autowired
  private lateinit var gson: Gson

}
