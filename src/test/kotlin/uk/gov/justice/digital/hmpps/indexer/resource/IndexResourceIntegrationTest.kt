package uk.gov.justice.digital.hmpps.indexer.resource

import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN

class IndexResourceIntegrationTest : QueueIntegrationTest() {

  @BeforeEach
  internal fun setUp() {
    indexAwsSqsClient.purgeQueue(PurgeQueueRequest(indexQueueUrl))
    CommunityApiExtension.communityApi.resetMappings()
  }

  @Nested
  inner class BuildIndex {
    @BeforeEach
    internal fun setUp() {
      deleteOffenderIndexes()
      initialiseIndexStatus()
    }

    @Nested
    inner class FirstTimeRun {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
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
            .jsonPath("$.currentIndexState").isEqualTo("ABSENT")
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
            .jsonPath("$.otherIndexState").isEqualTo("ABSENT")

        await untilCallTo { getIndexCount("offender") } matches { it == 1L }
      }
    }

    @Nested
    inner class SecondTimeRun {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
      }

      @Test
      internal fun `will build the blue index`() {
        webTestClient.put()
            .uri("/probation-index/build-index")
            .accept(MediaType.APPLICATION_JSON)
            .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentIndex").isEqualTo("GREEN")
            .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
            .jsonPath("$.otherIndex").isEqualTo("BLUE")
            .jsonPath("$.otherIndexState").isEqualTo("BUILDING")

        await untilCallTo { getIndexCount(BLUE) } matches { it == 3L }

        webTestClient.put()
            .uri("/probation-index/mark-complete")
            .accept(MediaType.APPLICATION_JSON)
            .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentIndex").isEqualTo("BLUE")
            .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
            .jsonPath("$.otherIndex").isEqualTo("GREEN")
            .jsonPath("$.otherIndexState").isEqualTo("COMPLETED")

        await untilCallTo { getIndexCount("offender") } matches { it == 3L }
      }

    }

    @Nested
    inner class SubsequentRuns {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        buildAndSwitchIndex(BLUE, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
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
            .jsonPath("$.currentIndex").isEqualTo("BLUE")
            .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
            .jsonPath("$.otherIndex").isEqualTo("GREEN")
            .jsonPath("$.otherIndexState").isEqualTo("BUILDING")

        await untilCallTo { getIndexCount(GREEN) } matches { it == 3L }

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
            .jsonPath("$.otherIndexState").isEqualTo("COMPLETED")

        await untilCallTo { getIndexCount("offender") } matches { it == 3L }
      }

    }

    @Nested
    inner class Paging {
      @BeforeEach
      internal fun setUp() {
        val crns = (1..31).asSequence().map { "X%05d".format(it) }.toList().toTypedArray()
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, *crns)
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
            .jsonPath("$.currentIndexState").isEqualTo("ABSENT")
            .jsonPath("$.otherIndex").isEqualTo("GREEN")
            .jsonPath("$.otherIndexState").isEqualTo("BUILDING")

        await untilCallTo { getIndexCount(GREEN) } matches { it == 31L }

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
            .jsonPath("$.otherIndexState").isEqualTo("ABSENT")

        await untilCallTo { getIndexCount("offender") } matches { it == 31L }
      }
    }
  }


  fun buildAndSwitchIndex(index: SyncIndex, expectedCount: Long) {
    webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

    await untilCallTo { getIndexCount(index) } matches { it == expectedCount }

    webTestClient.put()
        .uri("/probation-index/mark-complete")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

    await untilCallTo { getIndexCount("offender") } matches { it == expectedCount }
  }
}

