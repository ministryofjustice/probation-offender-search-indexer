package uk.gov.justice.digital.hmpps.indexer.resource

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.helpers.indexStatus
import uk.gov.justice.digital.hmpps.indexer.integration.ResourceIntegrationTest
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.BuildIndexError
import uk.gov.justice.digital.hmpps.indexer.service.CancelBuildIndexError
import uk.gov.justice.digital.hmpps.indexer.service.MarkBuildCompleteError

class IndexResourceApiTest : ResourceIntegrationTest() {

  @BeforeEach
  fun `reset mocks`() {
    Mockito.reset(indexService)
  }

  @Nested
  inner class BuildIndex {
    @Test
    fun `Request build index is successful and calls service`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexService.buildIndex()).thenReturn(expectedIndexStatus.right())

      webTestClient.put()
          .uri("/probation-index/build-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.otherIndex").isEqualTo("BLUE")
          .jsonPath("$.otherIndexState").isEqualTo("BUILDING")

      verify(indexService).buildIndex()
    }

    @Test
    fun `Request build index without role is forbidden`() {
      webTestClient.put()
          .uri("/probation-index/build-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation())
          .exchange()
          .expectStatus().isForbidden

      verify(indexService, never()).buildIndex()
    }

    @Test
    fun `Request build index already building returns conflict`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.BUILDING)
      whenever(indexService.buildIndex()).thenReturn(BuildIndexError.BuildAlreadyInProgress(expectedIndexStatus).left())

      webTestClient.put()
          .uri("/probation-index/build-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody()
          .jsonPath("$.message").value<String> { message ->
            assertThat(message).contains(expectedIndexStatus.otherIndex.name)
            assertThat(message).contains(expectedIndexStatus.otherIndexState.name)
          }

      verify(indexService).buildIndex()
    }

    @Test
    fun `Request build index requires valid token`() {
      webTestClient.put()
          .uri("/probation-index/build-index")
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
    }

  }

  @Nested
  inner class MarkIndexComplete {
    @Test
    fun `Request to mark index complete is successful and calls service`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.COMPLETED)
      whenever(indexService.markIndexingComplete()).thenReturn(expectedIndexStatus.right())

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
    fun `Request to mark index complete when index not building returns error`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.COMPLETED)
      whenever(indexService.markIndexingComplete()).thenReturn(MarkBuildCompleteError.BuildNotInProgress(expectedIndexStatus).left())

      webTestClient.put()
          .uri("/probation-index/mark-complete")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody()
          .jsonPath("$.message").value<String> { message ->
            assertThat(message).contains(expectedIndexStatus.otherIndex.name)
            assertThat(message).contains(expectedIndexStatus.otherIndexState.name)
          }

      verify(indexService).markIndexingComplete()
    }

  }

  @Nested
  inner class CancelIndexing {
    @Test
    fun `Request to cancel indexing is successful and calls service`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.CANCELLED)
      whenever(indexService.buildIndex()).thenReturn(expectedIndexStatus.right())

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
    fun `Request to mark index cancelled when index not building returns error`() {
      val expectedIndexStatus = indexStatus(otherIndex = SyncIndex.BLUE, otherIndexState = IndexState.CANCELLED)
      whenever(indexService.cancelIndexing()).thenReturn(CancelBuildIndexError.BuildNotInProgress(expectedIndexStatus).left())

      webTestClient.put()
          .uri("/probation-index/cancel-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody()
          .jsonPath("$.message").value<String> { message ->
            assertThat(message).contains(expectedIndexStatus.otherIndex.name)
            assertThat(message).contains(expectedIndexStatus.otherIndexState.name)
          }

      verify(indexService).cancelIndexing()
    }
  }

  @Nested
  inner class IndexOffender {
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
}