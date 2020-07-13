package uk.gov.justice.digital.hmpps.indexer.resource

import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class SearchResourceTest : ResourceIntegrationTest() {

  @Test
  fun `Match returns success`() {
    webTestClient.post()
        .uri("/probation-search/match")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk

    verify(searchService).match()
  }

  @Test
  fun `Match requires valid token`() {
    webTestClient.post()
        .uri("/probation-search/match")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
  }
}