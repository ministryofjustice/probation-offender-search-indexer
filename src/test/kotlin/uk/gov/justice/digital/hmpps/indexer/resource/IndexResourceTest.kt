package uk.gov.justice.digital.hmpps.indexer.resource

import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ["test"])
class IndexResourceTest {

  @Autowired
  private lateinit var webTestClient: WebTestClient

  @Autowired
  private lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  private lateinit var indexService: IndexService

  @Test
  fun `Request rebuild index is successful and calls service`() {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk

    verify(indexService).rebuildIndex()
  }

  @Test
  fun `Request rebuild index requires valid token`() {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
  }

  internal fun setAuthorisation(user: String = "probation-offender-search-indexer-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit {
    val token = jwtAuthHelper.createJwt(subject = user,
        scope = listOf("read"),
        expiryTime = Duration.ofHours(1L),
        roles = roles)
    return { it.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
  }

}