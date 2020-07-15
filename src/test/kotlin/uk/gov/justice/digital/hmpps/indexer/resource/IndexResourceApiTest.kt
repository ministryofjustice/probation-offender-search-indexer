package uk.gov.justice.digital.hmpps.indexer.resource

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.ResourceIntegrationTest
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.BuildIndexError
import java.time.LocalDateTime

class IndexResourceApiTest : ResourceIntegrationTest() {

  @Test
  fun `Request rebuild index is successful and calls service`() {
    val expectedIndexStatus = IndexStatus(currentIndex = SyncIndex.BLUE, startIndexTime = LocalDateTime.now(), endIndexTime = null, state = IndexState.BUILDING)
    whenever(indexService.buildIndex()).thenReturn(expectedIndexStatus.right())

    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.currentIndex").isEqualTo("BLUE")
        .jsonPath("$.state").isEqualTo("BUILDING")

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
  fun `Request rebuild index already building returns conflict`() {
    val expectedIndexStatus = IndexStatus(currentIndex = SyncIndex.BLUE, startIndexTime = LocalDateTime.now(), endIndexTime = null, state = IndexState.BUILDING)
    whenever(indexService.buildIndex()).thenReturn(BuildIndexError.BuildAlreadyInProgress(expectedIndexStatus).left())

    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.message").value<String> {message ->
          assertThat(message).contains("BLUE")
          assertThat(message).contains("BUILDING")
        }

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

  @Test
  fun `Request to cancel indexing is successful and calls service`() {
    webTestClient.put()
        .uri("/probation-index/cancel-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

    verify(indexService).cancelIndexing()
  }

  @Test
  fun `Request to cancel indexing without role is forbidden`() {
    webTestClient.put()
        .uri("/probation-index/cancel-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden

    verify(indexService, never()).cancelIndexing()
  }

  @Test
  fun `Request to cancel indexing requires valid token`() {
    webTestClient.put()
        .uri("/probation-index/cancel-index")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized

    verify(indexService, never()).cancelIndexing()
  }

  @Test
  fun `Request to index offender is successful and calls service`() {
    doReturn("{}").whenever(indexService).indexOffender("SOME_CRN")

    webTestClient.put()
        .uri("/probation-index/index/offender/SOME_CRN")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

    verify(indexService).indexOffender("SOME_CRN")
  }

  @Test
  fun `Request to index offender without role is forbidden`() {
    webTestClient.put()
        .uri("/probation-index/index/offender/SOME_CRN")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden

    verify(indexService, never()).indexOffender("SOME_CRN")
  }

  @Test
  fun `Request to index offender requires valid token`() {
    webTestClient.put()
        .uri("/probation-index/index/offender/SOME_CRN")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized

    verify(indexService, never()).indexOffender("SOME_CRN")
  }

}