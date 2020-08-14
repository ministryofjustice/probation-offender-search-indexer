package uk.gov.justice.digital.hmpps.indexer.integration.resource

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.service.BuildAlreadyInProgressError
import uk.gov.justice.digital.hmpps.indexer.service.BuildNotInProgressError
import uk.gov.justice.digital.hmpps.indexer.service.NoActiveIndexesError
import uk.gov.justice.digital.hmpps.indexer.service.OffenderNotFoundError

class IndexResourceApiTest : IntegrationTestBase() {

  @BeforeEach
  fun `reset mocks`() {
    Mockito.reset(indexService)
  }

  @Nested
  @TestInstance(PER_CLASS)
  inner class SecureEndpoints() {
    private fun secureEndpoints() =
        listOf("/probation-index/build-index", "/probation-index/mark-complete", "/probation-index/cancel-index",
            "/probation-index/index/offender/SOME_CRN", "/probation-index/purge-index-dlq", "/probation-index/transfer-event-dlq",
            "/probation-index/transfer-index-dlq")

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires a valid authentication token`(uri: String) {
      webTestClient.put()
          .uri(uri)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
    }

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires the correct role`(uri: String) {
      webTestClient.put()
          .uri(uri)
          .headers(setAuthorisation(roles = listOf()))
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
    }

  }

  @Nested
  inner class BuildIndex {
    @Test
    fun `Request build index is successful and calls service`() {
      doReturn(IndexStatus(currentIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING).right()).whenever(indexService)
          .prepareIndexForRebuild()


      webTestClient.put()
          .uri("/probation-index/build-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.otherIndex").isEqualTo("BLUE")
          .jsonPath("$.otherIndexState").isEqualTo("BUILDING")

      verify(indexService).prepareIndexForRebuild()
    }

    @Test
    fun `Request build index already building returns conflict`() {
      val expectedIndexStatus = IndexStatus(currentIndex = SyncIndex.GREEN, otherIndexState = IndexState.BUILDING)
      doReturn(BuildAlreadyInProgressError(expectedIndexStatus).left()).whenever(indexService).prepareIndexForRebuild()

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

      verify(indexService).prepareIndexForRebuild()
    }

  }

  @Nested
  inner class MarkIndexComplete {
    @Test
    fun `Request to mark index complete is successful and calls service`() {
      doReturn(anIndexStatus().right()).whenever(indexService).markIndexingComplete()

      webTestClient.put()
          .uri("/probation-index/mark-complete")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

      verify(indexService).markIndexingComplete()
    }

    @Test
    fun `Request to mark index complete when index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = SyncIndex.GREEN, otherIndexState = IndexState.COMPLETED)
      doReturn(BuildNotInProgressError(expectedIndexStatus).left()).whenever(indexService).markIndexingComplete()

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
      doReturn(anIndexStatus().right()).whenever(indexService).cancelIndexing()

      webTestClient.put()
          .uri("/probation-index/cancel-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

      verify(indexService).cancelIndexing()
    }

    @Test
    fun `Request to mark index cancelled when index not building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndex = SyncIndex.GREEN, otherIndexState = IndexState.CANCELLED)
      doReturn(BuildNotInProgressError(expectedIndexStatus).left()).whenever(indexService).cancelIndexing()

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
      doReturn("{}".right()).whenever(indexService).updateOffender("SOME_CRN")

      webTestClient.put()
          .uri("/probation-index/index/offender/SOME_CRN")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

      verify(indexService).updateOffender("SOME_CRN")
    }

    @Test
    fun `Request to index offender without active indexes returns conflict`() {
      val expectedIndexStatus = IndexStatus.newIndex()
      doReturn(NoActiveIndexesError(expectedIndexStatus).left()).whenever(indexService).updateOffender("SOME_CRN")

      webTestClient.put()
          .uri("/probation-index/index/offender/SOME_CRN")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(409)

      verify(indexService).updateOffender("SOME_CRN")
    }

    @Test
    fun `Request to index unknown offender returns not found`() {
      doReturn(OffenderNotFoundError("SOME_CRN").left()).whenever(indexService).updateOffender("SOME_CRN")

      webTestClient.put()
          .uri("/probation-index/index/offender/SOME_CRN")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(404)

      verify(indexService).updateOffender("SOME_CRN")
    }
  }

}


fun anIndexStatus() = IndexStatus.newIndex()