package uk.gov.justice.digital.hmpps.indexer.resource

import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.ResourceIntegrationTest

class IndexResourceTest : ResourceIntegrationTest() {

  @Test
  fun `Request rebuild index is successful and calls service`() {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

    verify(indexService).buildIndex()
  }

  @Test
  fun `Request rebuild index without role is forbidden`() {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden

    verify(indexService, never()).buildIndex()
  }

  @Test
  fun `Request rebuild index requires valid token`() {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
  }

  @Test
  fun `Request to mark index complete is successful and calls service`() {
    webTestClient.put()
        .uri("/probation-index/mark-complete")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

    verify(indexService).markIndexingComplete()
  }

  @Test
  fun `Request to mark index complete without role is forbidden`() {
    webTestClient.put()
        .uri("/probation-index/mark-complete")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden

    verify(indexService, never()).markIndexingComplete()
  }

  @Test
  fun `Request to mark index complete requires valid token`() {
    webTestClient.put()
        .uri("/probation-index/mark-complete")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized

    verify(indexService, never()).markIndexingComplete()
  }

}