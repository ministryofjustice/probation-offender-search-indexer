package uk.gov.justice.digital.hmpps.indexer.resource

import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class IndexResourceTest : ResourceIntegrationTest() {

  @Test
  fun `Request rebuild index is successful and calls service`() {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk

    verify(indexService).buildIndex()
  }

  @Test
  fun `Request rebuild index requires valid token`() {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
  }

}