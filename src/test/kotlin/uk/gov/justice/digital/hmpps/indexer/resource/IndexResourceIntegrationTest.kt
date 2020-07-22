package uk.gov.justice.digital.hmpps.indexer.resource

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN


class IndexResourceIntegrationTest : QueueIntegrationTest() {

  @Nested
  inner class BuildIndex {
    @BeforeEach
    internal fun setUp() {
      CommunityApiExtension.communityApi.stubAllOffenderGets("X12345")
    }

    @Nested
    inner class FirstTimeRun {
      @BeforeEach
      internal fun setUp() {
        deleteIndexes()
      }

      @Test
      internal fun `will build the green index`() {
        webTestClient.put()
            .uri("/probation-index/build-index")
            .accept(MediaType.APPLICATION_JSON)
            .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentIndex").isEqualTo("NONE")
            .jsonPath("$.currentIndexState").isEqualTo("NEW")
            .jsonPath("$.otherIndex").isEqualTo("GREEN")
            .jsonPath("$.otherIndexState").isEqualTo("BUILDING")

        await untilCallTo { getIndexCount(GREEN) } matches { it == 1L }

        webTestClient.put()
            .uri("/probation-index/mark-complete")
            .accept(MediaType.APPLICATION_JSON)
            .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentIndex").isEqualTo("GREEN")
            .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
            .jsonPath("$.otherIndex").isEqualTo("BLUE")
            .jsonPath("$.otherIndexState").isEqualTo("NEW")

        await untilCallTo { getIndexCount("offender") } matches { it == 1L }
      }
    }

    @Nested
    inner class SecondTimeRun {

    }

    @Nested
    inner class SubsequentTimeRuns {

    }
  }
}

