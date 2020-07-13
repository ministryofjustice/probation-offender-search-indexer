package uk.gov.justice.digital.hmpps.indexer.resource

import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class SearchResourceTest : ResourceIntegrationTest() {

  @Test
  fun `Match returns success`() {
    webTestClient.put()
        .uri("/probation-search/match")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .exchange()
        .expectStatus().isOk

    verify(searchService).match()
  }

  @Test
  fun `Match returns forbidden without correct role`() {
    webTestClient.put()
        .uri("/probation-search/match")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden

    verify(searchService, never()).match()
  }

  @Test
  fun `Match return unauthorised without a valid token`() {
    webTestClient.put()
        .uri("/probation-search/match")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized

    verify(searchService, never()).match()
  }
}