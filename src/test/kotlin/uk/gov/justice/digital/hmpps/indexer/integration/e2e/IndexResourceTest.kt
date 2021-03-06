package uk.gov.justice.digital.hmpps.indexer.integration.e2e

import com.nhaarman.mockitokotlin2.whenever
import org.apache.lucene.search.join.ScoreMode
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.TermQueryBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.helpers.findLogAppender
import uk.gov.justice.digital.hmpps.indexer.helpers.hasLogMessageContaining
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.OffenderAlias
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.OffenderManager
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.ProbationArea
import uk.gov.justice.digital.hmpps.indexer.listeners.IndexListener
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.ABSENT
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.indexer.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.service.OffenderDetail
import java.time.Duration

class IndexResourceTest : IntegrationTestBase() {

  @Nested
  inner class BuildIndexAndMarkComplete {
    @BeforeEach
    internal fun setUp() {
      initialiseIndexStatus()
    }

    @Nested
    inner class FirstTimeRun {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        CommunityApiExtension.communityApi.stubGetProbationStatus()
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
        CommunityApiExtension.communityApi.stubGetProbationStatus()
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
        CommunityApiExtension.communityApi.stubGetProbationStatus()
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

      @Test
      internal fun `will NOT complete the index build if threshold not reached and not ignored`() {
        whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

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
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      internal fun `will complete the index build if threshold not reached but threshold is ignored`() {
        whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)

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
          .uri("/probation-index/mark-complete?ignoreThreshold=true")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.currentIndex").isEqualTo("GREEN")
          .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
          .jsonPath("$.otherIndex").isEqualTo("BLUE")
          .jsonPath("$.otherIndexState").isEqualTo("COMPLETED")
      }
    }

    @Nested
    inner class SwitchIndexes {
      @Test
      internal fun `Switch index`() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12346")
        buildAndSwitchIndex(GREEN, 1)
        buildAndSwitchIndex(BLUE, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12346")
        CommunityApiExtension.communityApi.stubGetProbationStatus()

        webTestClient.get()
          .uri("/info")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("index-status.id").doesNotExist()
          .jsonPath("index-status.currentIndex").isEqualTo("BLUE")
          .jsonPath("index-status.otherIndex").isEqualTo("GREEN")
          .jsonPath("index-size.GREEN").isEqualTo(1)
          .jsonPath("index-size.BLUE").isEqualTo(1)
          .jsonPath("offender-alias").isEqualTo("probation-search-blue")
          .jsonPath("index-queue-backlog").isEqualTo("0")

        webTestClient.put()
          .uri("/probation-index/switch-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.currentIndex").isEqualTo("GREEN")
          .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
          .jsonPath("$.otherIndex").isEqualTo("BLUE")
          .jsonPath("$.otherIndexState").isEqualTo("COMPLETED")

        webTestClient.get()
          .uri("/info")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("index-status.id").doesNotExist()
          .jsonPath("index-status.currentIndex").isEqualTo("GREEN")
          .jsonPath("index-status.otherIndex").isEqualTo("BLUE")
          .jsonPath("index-size.GREEN").isEqualTo(1)
          .jsonPath("index-size.BLUE").isEqualTo(1)
          .jsonPath("offender-alias").isEqualTo("probation-search-green")
          .jsonPath("index-queue-backlog").isEqualTo("0")

        webTestClient.put()
          .uri("/probation-index/switch-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.currentIndex").isEqualTo("BLUE")
          .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
          .jsonPath("$.otherIndex").isEqualTo("GREEN")
          .jsonPath("$.otherIndexState").isEqualTo("COMPLETED")
      }

      @Test
      internal fun `Will not switch indexes if both one is in progress`() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetProbationStatus()

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
          .uri("/probation-index/switch-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      internal fun `Will not switch indexes if one index is cancelled`() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetProbationStatus()

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

        webTestClient.put()
          .uri("/probation-index/cancel-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.currentIndex").isEqualTo("GREEN")
          .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
          .jsonPath("$.otherIndex").isEqualTo("BLUE")
          .jsonPath("$.otherIndexState").isEqualTo("CANCELLED")

        webTestClient.put()
          .uri("/probation-index/switch-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      internal fun `Will not switch indexes if one index is absent`() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetProbationStatus()

        webTestClient.put()
          .uri("/probation-index/switch-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      internal fun `Will switch indexes if both one is in progress but force set to true`() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetProbationStatus()

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
          .uri("/probation-index/switch-index?force=true")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.currentIndex").isEqualTo("BLUE")
          .jsonPath("$.currentIndexState").isEqualTo("CANCELLED")
          .jsonPath("$.otherIndex").isEqualTo("GREEN")
          .jsonPath("$.otherIndexState").isEqualTo("COMPLETED")
      }

      @Test
      internal fun `Will switch indexes if one index is cancelled but force set to true`() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetProbationStatus()

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

        webTestClient.put()
          .uri("/probation-index/cancel-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.currentIndex").isEqualTo("GREEN")
          .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
          .jsonPath("$.otherIndex").isEqualTo("BLUE")
          .jsonPath("$.otherIndexState").isEqualTo("CANCELLED")

        webTestClient.put()
          .uri("/probation-index/switch-index?force=true")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.currentIndex").isEqualTo("BLUE")
          .jsonPath("$.currentIndexState").isEqualTo("CANCELLED")
          .jsonPath("$.otherIndex").isEqualTo("GREEN")
          .jsonPath("$.otherIndexState").isEqualTo("COMPLETED")
      }

      @Test
      internal fun `Will not switch indexes if one index is absent and force set to true`() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        buildAndSwitchIndex(GREEN, 1)
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetProbationStatus()

        webTestClient.put()
          .uri("/probation-index/switch-index?force=true")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }
    }

    @Nested
    inner class Paging {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = 31)
        CommunityApiExtension.communityApi.stubGetProbationStatus()
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

    @Nested
    inner class PopulateOffenderError {

      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = 3)
        CommunityApiExtension.communityApi.stubGetProbationStatus()
      }

      @Test
      internal fun `will show the crn if any populate offender message fails`() {
        val logAppender = findLogAppender(IndexListener::class.java)
        CommunityApiExtension.communityApi.stubOffenderNotFound("X00002")

        webTestClient.put()
          .uri("/probation-index/build-index")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

        await untilCallTo { getIndexCount(GREEN) } matches { it == 2L }

        webTestClient.put()
          .uri("/probation-index/mark-complete")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

        await untilCallTo { getIndexCount("offender") } matches { it == 2L }
        await untilCallTo { logAppender.list } matches { it hasLogMessageContaining "OffenderNotFoundError(crn=X00002)" }
      }
    }
  }

  @Nested
  inner class BuildIndexAndCancel {
    @BeforeEach
    internal fun setUp() {
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = 1)
      buildAndSwitchIndex(GREEN, 1)
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = 20)
      CommunityApiExtension.communityApi.stubGetProbationStatus()
    }

    @Test
    internal fun `will cancel a build index`() {
      assertThat(getIndexCount("offender")).isEqualTo(1)

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

      webTestClient.put()
        .uri("/probation-index/cancel-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.currentIndex").isEqualTo("GREEN")
        .jsonPath("$.currentIndexState").isEqualTo("COMPLETED")
        .jsonPath("$.otherIndex").isEqualTo("BLUE")
        .jsonPath("$.otherIndexState").isEqualTo("CANCELLED")

      assertThat(getIndexCount("offender")).isEqualTo(1)
      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
    }
  }

  @Nested
  inner class IndexOffender {
    @BeforeEach
    internal fun setUp() {
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
      CommunityApiExtension.communityApi.stubGetProbationStatus()
      buildAndSwitchIndex(GREEN, 1)
    }

    @Nested
    inner class NewOffender {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubGetOffender("X99999")
        CommunityApiExtension.communityApi.stubGetProbationStatus()
      }

      @Test
      internal fun `will add offender to elastic search`() {
        assertThat(getIndexCount(GREEN)).isEqualTo(1)
        assertThat(getIndexCount("offender")).isEqualTo(1)

        webTestClient.put()
          .uri("/probation-index/index/offender/{crn}", "X99999")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.otherIds.crn").isEqualTo("X99999")

        await untilCallTo { getIndexCount(GREEN) } matches { it == 2L }
        await untilCallTo { getIndexCount("offender") } matches { it == 2L }
      }
    }

    @Nested
    inner class ExistingOffender {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubGetOffender(crn = "X99999", nomsNumber = "A9999BB")
        CommunityApiExtension.communityApi.stubGetProbationStatus()
      }

      @Test
      internal fun `will update offender in elastic search`() {
        webTestClient.put()
          .uri("/probation-index/index/offender/{crn}", "X99999")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.otherIds.crn").isEqualTo("X99999")

        await untilCallTo { getIndexCount("offender") } matches { it == 2L }
        await untilCallTo { nomsNumberOf("X99999") } matches { it == "A9999BB" }

        CommunityApiExtension.communityApi.stubGetOffender(crn = "X99999", nomsNumber = "A0000BB")

        webTestClient.put()
          .uri("/probation-index/index/offender/{crn}", "X99999")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.otherIds.crn").isEqualTo("X99999")

        await untilCallTo { nomsNumberOf("X99999") } matches { it == "A0000BB" }
        await untilCallTo { getIndexCount("offender") } matches { it == 2L }
      }
    }

    @Nested
    inner class IndexBehaviour {
      @BeforeEach
      internal fun setUp() {
        initialiseIndexStatus()
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetOffender(crn = "X12345", pncNumber = "1999/0460155D")
        CommunityApiExtension.communityApi.stubGetOffender(crn = "X12346", pncNumber = "1998/0460155D")
        CommunityApiExtension.communityApi.stubGetOffender(crn = "X12347", pncNumber = "1999/9460155D")
        CommunityApiExtension.communityApi.stubGetProbationStatus()
        buildAndSwitchIndex(GREEN, 3)
      }

      @Nested
      inner class PNCMappingShortForm {
        @Test
        internal fun `can search by pnc number when in canonical form`() {
          val results = search(QueryBuilders.matchQuery("otherIds.pncNumberShortYear", "99/460155d"))
          assertThat(results.hits.asList()).extracting<String> { it.id }.containsExactly("X12345")
        }

        @Test
        internal fun `must search pnc number using using canonical form`() {
          val results = search(QueryBuilders.matchQuery("otherIds.pncNumberShortYear", "99/0460155d"))
          assertThat(results.hits).hasSize(0)
        }

        @Test
        internal fun `must search by pnc number as lowercase`() {
          val results = search(QueryBuilders.matchQuery("otherIds.pncNumberShortYear", "99/460155D"))
          assertThat(results.hits).hasSize(0)
        }
      }

      @Nested
      inner class PNCMappingLongForm {
        @Test
        internal fun `can search by pnc number when in canonical form`() {
          val results = search(QueryBuilders.matchQuery("otherIds.pncNumberLongYear", "1999/460155d"))
          assertThat(results.hits.asList()).extracting<String> { it.id }.containsExactly("X12345")
        }

        @Test
        internal fun `must search pnc number using using canonical form`() {
          val results = search(QueryBuilders.matchQuery("otherIds.pncNumberLongYear", "1999/0460155d"))
          assertThat(results.hits).hasSize(0)
        }

        @Test
        internal fun `must search by pnc number as lowercase`() {
          val results = search(QueryBuilders.matchQuery("otherIds.pncNumberLongYear", "1999/460155D"))
          assertThat(results.hits).hasSize(0)
        }
      }

      @Nested
      inner class CROMapping {
        @BeforeEach
        fun setup() {
          initialiseIndexStatus()
          CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12345", croNumber = "46189/08G")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12346", croNumber = "46189/99G")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12347", croNumber = "99999/08G")
          CommunityApiExtension.communityApi.stubGetProbationStatus()
          buildAndSwitchIndex(GREEN, 3)
        }

        @Test
        internal fun `can search by cro number when in lowercase form`() {
          val results = search(QueryBuilders.matchQuery("otherIds.croNumberLowercase", "46189/08g"))
          assertThat(results.hits.asList()).extracting<String> { it.id }.containsExactly("X12345")
        }

        @Test
        internal fun `must search by cro number as lowercase`() {
          val results = search(QueryBuilders.matchQuery("otherIds.croNumberLowercase", "46189/08G"))
          assertThat(results.hits).hasSize(0)
        }
      }

      @Nested
      inner class ProbationAreaMapping {
        @BeforeEach
        fun setup() {
          initialiseIndexStatus()
          CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346")
          CommunityApiExtension.communityApi.stubGetOffender(
            crn = "X12345",
            offenderManagers = listOf(
              OffenderManager(probationArea = ProbationArea(code = "N01"), active = true),
              OffenderManager(probationArea = ProbationArea(code = "N02"), active = false)
            )
          )
          CommunityApiExtension.communityApi.stubGetOffender(
            crn = "X12346",
            offenderManagers = listOf(
              OffenderManager(probationArea = ProbationArea(code = "N01"), active = false),
              OffenderManager(probationArea = ProbationArea(code = "N02"), active = true)
            )
          )
          CommunityApiExtension.communityApi.stubGetProbationStatus()
          buildAndSwitchIndex(GREEN, 2)
        }

        @Test
        internal fun `offender managers are nested so can search using attributes of a specific manager`() {
          val query = QueryBuilders.boolQuery()
          query.must(QueryBuilders.matchQuery("offenderManagers.probationArea.code", "N01"))
          query.must(TermQueryBuilder("offenderManagers.active", true))

          val results = search(QueryBuilders.nestedQuery("offenderManagers", query, ScoreMode.Max))
          assertThat(results.hits.asList()).extracting<String> { it.id }.containsExactly("X12345")
        }
      }

      @Nested
      inner class AliasMapping {
        @BeforeEach
        fun setup() {
          initialiseIndexStatus()
          CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346")
          CommunityApiExtension.communityApi.stubGetOffender(
            crn = "X12345",
            offenderAliases = listOf(
              OffenderAlias(surname = "Smith", firstName = "Jane", gender = "FEMALE", dateOfBirth = "1965-07-19"),
              OffenderAlias(surname = "Kunis", firstName = "Ella", gender = "FEMALE", dateOfBirth = "1965-07-19")
            )
          )
          CommunityApiExtension.communityApi.stubGetOffender(
            crn = "X12346",
            offenderAliases = listOf(
              OffenderAlias(surname = "Smith", firstName = "Ella", gender = "FEMALE", dateOfBirth = "1965-07-19"),
              OffenderAlias(surname = "Kunis", firstName = "Jane", gender = "FEMALE", dateOfBirth = "1965-07-19")
            )
          )
          CommunityApiExtension.communityApi.stubGetProbationStatus()
          buildAndSwitchIndex(GREEN, 2)
        }

        @Test
        internal fun `legacy applications can still use a non-nested search though not recommended`() {
          val query = QueryBuilders.boolQuery()
          query.must(QueryBuilders.matchQuery("offenderAliases.surname", "Smith"))
          query.must(QueryBuilders.matchQuery("offenderAliases.firstName", "Jane"))
          val results = search(query)

          assertThat(results.hits.asList()).extracting<String> { it.id }.containsExactlyInAnyOrder("X12345", "X12346")
        }

        @Test
        internal fun `offender aliases are nested so can search using attributes of a alias`() {
          val query = QueryBuilders.boolQuery()
          query.must(QueryBuilders.matchQuery("offenderAliases.surname", "Smith"))
          query.must(QueryBuilders.matchQuery("offenderAliases.firstName", "Jane"))

          val results = search(QueryBuilders.nestedQuery("offenderAliases", query, ScoreMode.Max))

          assertThat(results.hits.asList()).extracting<String> { it.id }.containsExactly("X12345")
        }
      }
    }
  }

  @Nested
  inner class ClearDeadLetterQueue {
    @Nested
    inner class IndexDLQ {
      @BeforeEach
      internal fun setUp() {
        repeat(10) {
          indexAwsSqsDlqClient.sendMessage(indexDlqUrl, "{}")
        }
        await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 10 }
      }

      @Test
      internal fun `will remove all message on index DLQ`() {
        webTestClient.put()
          .uri("/probation-index/purge-index-dlq")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

        await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 0 }
      }
    }

    @Nested
    inner class EventDLQ {
      @BeforeEach
      internal fun setUp() {
        repeat(10) {
          eventAwsSqsDlqClient.sendMessage(eventDlqUrl, "{}")
        }
        await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == 10 }
      }

      @Test
      internal fun `will remove all message on event DLQ`() {
        webTestClient.put()
          .uri("/probation-index/purge-event-dlq")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

        await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == 0 }
      }
    }
  }

  @Nested
  inner class TransferEventDeadLetterQueue {
    @Nested
    inner class EventDLQ {

      private val crns = listOf("X12346", "X12347", "X12348")

      @BeforeEach
      internal fun setUp() {
        initialiseIndexStatus()
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
        CommunityApiExtension.communityApi.stubGetProbationStatus()
        buildAndSwitchIndex(GREEN, 1)

        crns.forEach {
          eventAwsSqsDlqClient.sendMessage(eventDlqUrl, offenderChangedMessage(it))
          CommunityApiExtension.communityApi.stubGetOffender(it)
        }
        await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == crns.size }
      }

      @Test
      internal fun `will move all message on event DLQ to event Queue`() {

        webTestClient.put()
          .uri("/probation-index/transfer-event-dlq")
          .accept(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
          .exchange()
          .expectStatus().isOk

        await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == 0 }
        await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
        await untilCallTo { getIndexCount(GREEN) } matches { it == (crns.size + 1).toLong() }
      }
    }
  }

  @Nested
  inner class TransferIndexDlq {

    @BeforeEach
    internal fun `build index but don't complete`() {
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
      CommunityApiExtension.communityApi.stubGetProbationStatus()
      buildIndex(GREEN, 1)
    }

    @Test
    internal fun `will move all message on index DLQ to index Queue and process them if building`() {
      indexAwsSqsDlqClient.sendMessage(
        indexDlqUrl,
        """
            {
              "type": "POPULATE_OFFENDER",
              "crn": "X12346"
            }
        """.trimIndent()
      )
      CommunityApiExtension.communityApi.stubGetOffender("X12346")
      CommunityApiExtension.communityApi.stubGetProbationStatus()

      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 1 }

      webTestClient.put()
        .uri("/probation-index/transfer-index-dlq")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 0 }
      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
      await untilCallTo { getIndexCount(GREEN) } matches { it == 2L }
    }
  }

  @Nested
  inner class HousekeepingBuildComplete {

    @BeforeEach
    fun createEmptyIndex() {
      initialiseIndexStatus()
    }

    @Test
    fun `marks build complete if it is finished`() {
      buildIndexAndWaitUntilFinished()

      callHousekeeping()

      assertBuildStatusComplete()
    }

    @Test
    fun `does nothing if build already completed`() {
      buildIndexAndWaitUntilFinished()
      markBuildComplete()
      val beforeHousekeepingStatus = indexStatusService.getIndexStatus()

      callHousekeeping()

      assertBuildStatusComplete()
      assertThat(indexStatusService.getIndexStatus()).isEqualTo(beforeHousekeepingStatus)
    }

    @Test
    fun `does nothing if build is still in progress`() {
      buildIndex(1000)
      val beforeHousekeepingStatus = indexStatusService.getIndexStatus()
      assertThat(indexQueueService.getIndexQueueStatus().active).isTrue

      callHousekeeping()

      assertBuildStatusBuilding()
      assertThat(indexStatusService.getIndexStatus()).isEqualTo(beforeHousekeepingStatus)

      cancelBuildAndWait() // Ensures there are no messages still active before next test
    }

    @Test
    fun `does nothing if build was not successful`() {
      buildIndexAndWaitUntilFinished()
      addMessageToDlq()
      val beforeHousekeepingStatus = indexStatusService.getIndexStatus()

      callHousekeeping()

      assertBuildStatusBuilding()
      assertThat(indexStatusService.getIndexStatus()).isEqualTo(beforeHousekeepingStatus)
    }

    @Test
    fun `does nothing if threshold not reached`() {
      whenever(indexBuildProperties.completeThreshold).thenReturn(1000000)
      buildIndexAndWaitUntilFinished()
      val beforeHousekeepingStatus = indexStatusService.getIndexStatus()

      callHousekeeping()

      assertBuildStatusBuilding()
      assertThat(indexStatusService.getIndexStatus()).isEqualTo(beforeHousekeepingStatus)
    }

    private fun buildIndexAndWaitUntilFinished() {
      buildIndex(1)
      await untilCallTo { getIndexCount(GREEN) } matches { it == 1L }
      assertBuildStatusBuilding()
    }

    private fun buildIndex(numberOfOffenders: Long) {
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = numberOfOffenders)
      webTestClient.put()
        .uri("/probation-index/build-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk
    }

    private fun markBuildComplete() =
      webTestClient.put()
        .uri("/probation-index/mark-complete")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

    private fun callHousekeeping() =
      webTestClient.put()
        .uri("/probation-index/queue-housekeeping")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

    private fun assertBuildStatusBuilding() =
      indexStatusService.getIndexStatus()
        .also { assertThat(it.currentIndexState).isEqualTo(ABSENT) }
        .also { assertThat(it.otherIndexState).isEqualTo(BUILDING) }

    private fun assertBuildStatusComplete() =
      indexStatusService.getIndexStatus()
        .also { assertThat(it.currentIndexState).isEqualTo(COMPLETED) }
        .also { assertThat(it.otherIndexState).isEqualTo(ABSENT) }

    private fun cancelBuildAndWait() {
      webTestClient.put()
        .uri("/probation-index/cancel-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
        .exchange()
        .expectStatus().isOk

      await.atMost(Duration.ofSeconds(20)) untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
    }

    private fun addMessageToDlq() = indexAwsSqsDlqClient.sendMessage(indexDlqUrl, "{}")
  }

  @Nested
  inner class HousekeepingIndexDlq {

    @BeforeEach
    fun setUp() {
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
      CommunityApiExtension.communityApi.stubGetProbationStatus()
      buildIndex(GREEN, 1)
    }

    @Test
    fun `will add good DLQ messages to the index`() {
      CommunityApiExtension.communityApi.stubGetOffender("X12346")
      indexAwsSqsDlqClient.sendMessage(
        indexDlqUrl,
        """
            {
              "type": "POPULATE_OFFENDER",
              "crn": "X12346"
            }
        """.trimIndent()
      )
      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 1 }

      webTestClient.put()
        .uri("/probation-index/queue-housekeeping")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 0 }
      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
      await untilCallTo { getIndexCount(GREEN) } matches { it == 2L }
    }

    @Test
    fun `will only complete build after 2nd housekeeping call if there are good DLQ messages`() {
      CommunityApiExtension.communityApi.stubGetOffender("X12346")
      indexAwsSqsDlqClient.sendMessage(
        indexDlqUrl,
        """
            {
              "type": "POPULATE_OFFENDER",
              "crn": "X12346"
            }
        """.trimIndent()
      )
      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 1 }

      webTestClient.put()
        .uri("/probation-index/queue-housekeeping")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 0 }
      await untilCallTo { indexQueueService.getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
      await untilCallTo { getIndexCount(GREEN) } matches { it == 2L }
      assertThat(indexStatusService.getIndexStatus().otherIndexState).isEqualTo(BUILDING)

      webTestClient.put()
        .uri("/probation-index/queue-housekeeping")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk
      assertThat(indexStatusService.getIndexStatus().otherIndexState).isEqualTo(ABSENT)
      assertThat(indexStatusService.getIndexStatus().currentIndexState).isEqualTo(COMPLETED)
    }
  }

  @Nested
  inner class HousekeepingEventDlq {

    @BeforeEach
    fun setUp() {
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
      CommunityApiExtension.communityApi.stubGetProbationStatus()
      buildIndex(GREEN, 1)
    }

    @Test
    fun `will add good DLQ messages to the index`() {
      CommunityApiExtension.communityApi.stubGetOffender("X12346")
      eventAwsSqsDlqClient.sendMessage(eventDlqUrl, offenderChangedMessage("X12346"))
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == 1 }

      webTestClient.put()
        .uri("/probation-index/queue-housekeeping")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == 0 }
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { getIndexCount(GREEN) } matches { it == 2L }
    }

    @Test
    fun `will not add bad DLQ messages to the index`() {
      // NOT stubbing the offender in community API Mock - hence the message is considered "bad"
      eventAwsSqsDlqClient.sendMessage(eventDlqUrl, offenderChangedMessage("X12346"))
      await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == 1 }

      webTestClient.put()
        .uri("/probation-index/queue-housekeeping")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk

      await untilCallTo { getNumberOfMessagesCurrentlyOnEventQueue() } matches { it == 0 }
      await untilCallTo { getNumberOfMessagesCurrentlyInFlight() } matches { it == 0 }
      await untilCallTo { getIndexCount(GREEN) } matches { it == 1L }
      // Unfortunately localstack doesn't put the message onto the DLQ when it fails - if they ever fix that then we can uncomment the next line
      // await untilCallTo { getNumberOfMessagesCurrentlyOnEventDLQ() } matches { it == 1 }
    }

    fun getNumberOfMessagesCurrentlyOnEventQueue(): Int {
      val queueAttributes = eventAwsSqsClient.getQueueAttributes(eventQueueUrl, listOf("ApproximateNumberOfMessages"))
      return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
    }

    fun getNumberOfMessagesCurrentlyOnEventDLQ(): Int {
      val queueAttributes = eventAwsSqsDlqClient.getQueueAttributes(eventDlqUrl, listOf("ApproximateNumberOfMessages"))
      return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
    }

    fun getNumberOfMessagesCurrentlyInFlight(): Int {
      val queueAttributes =
        eventAwsSqsClient.getQueueAttributes(eventQueueUrl, listOf("ApproximateNumberOfMessagesNotVisible"))
      return queueAttributes.attributes["ApproximateNumberOfMessagesNotVisible"]?.toInt() ?: 0
    }
  }

  fun nomsNumberOf(crn: String): String? {
    val offender = getById(index = "offender", crn = crn)
    val offenderDetail = gson.fromJson<OffenderDetail>(offender, OffenderDetail::class.java)
    return offenderDetail.otherIds.nomsNumber
  }

  fun offenderChangedMessage(crn: String) =
    """
    {
      "Type": "Notification",
      "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
      "TopicArn": "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
      "Message": "{\"offenderId\":490001467,\"crn\":\"$crn\",\"nomsNumber\":\"A1234BC\"}",
      "Timestamp": "2020-02-25T11:25:16.169Z",
      "SignatureVersion": "1",
      "Signature": "h5p3FnnbsSHxj53RFePh8HR40cbVvgEZa6XUVTlYs/yuqfDsi17MPA+bX4ijKmmTT2l6xG2xYhcmRAbJWQ4wrwncTBm2azgiwSO5keRNWYVdiC0rI484KLZboP1SDsE+Y7hOU/R0dz49q7+0yd+QIocPteKB/8xG7/6kjGStAZKf3cEdlxOwLhN+7RU1Yk2ENuwAJjVRtvlAa76yKB3xvL2hId7P7ZLmHGlzZDNZNYxbg9C8HGxteOzZ9ZeeQsWDf9jmZ+5+7dKXQoW9LeqwHxEAq2vuwSZ8uwM5JljXbtS5w1P0psXPYNoin2gU1F5MDK8RPzjUtIvjINx08rmEOA==",
      "SigningCertURL": "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-a86cb10b4e1f29c941702d737128f7b6.pem",
      "UnsubscribeURL": "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:92545cfe-de5d-43e1-8339-c366bf0172aa",
      "MessageAttributes": {
        "eventType": {
          "Type": "String",
          "Value": "OFFENDER_CHANGED"
        },
        "id": {
          "Type": "String",
          "Value": "cb4645f2-d0c1-4677-806a-8036ed54bf69"
        },
        "contentType": {
          "Type": "String",
          "Value": "text/plain;charset=UTF-8"
        },
        "timestamp": {
          "Type": "Number.java.lang.Long",
          "Value": "1582629916147"
        }
      }
    }

    """.trimIndent()
}
