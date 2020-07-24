package uk.gov.justice.digital.hmpps.indexer.integration.e2e

import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.elasticsearch.index.query.QueryBuilders
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.service.OffenderDetail

class IndexResourceTest : IntegrationTestBase() {

  @BeforeEach
  internal fun setUp() {
    indexAwsSqsClient.purgeQueue(PurgeQueueRequest(indexQueueUrl))
    CommunityApiExtension.communityApi.resetMappings()
  }

  @Nested
  inner class BuildIndexAndMarkComplete {
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
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = 31)
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

  @Nested
  inner class BuildIndexAndCancel {
    @BeforeEach
    internal fun setUp() {
      deleteOffenderIndexes()
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = 1)
      buildAndSwitchIndex(GREEN, 1)
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, numberOfOffenders = 20)
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
      await untilCallTo { getNumberOfMessagesCurrentlyOnIndexQueue() } matches { it == 0 }
    }
  }

  @Nested
  inner class IndexOffender {
    @BeforeEach
    internal fun setUp() {
      deleteOffenderIndexes()
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345")
      buildAndSwitchIndex(GREEN, 1)
    }

    @Nested
    inner class NewOffender {
      @BeforeEach
      internal fun setUp() {
        CommunityApiExtension.communityApi.stubGetOffender("X99999")
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
        deleteOffenderIndexes()
        initialiseIndexStatus()
        CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
        CommunityApiExtension.communityApi.stubGetOffender(crn = "X12345", pncNumber = "1999/0460155D")
        CommunityApiExtension.communityApi.stubGetOffender(crn = "X12346", pncNumber = "1998/0460155D")
        CommunityApiExtension.communityApi.stubGetOffender(crn = "X12347", pncNumber = "1999/9460155D")
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
    }
  }


  fun nomsNumberOf(crn: String): String? {
    val offender = getById(index = "offender", crn = crn)
    val offenderDetail = gson.fromJson<OffenderDetail>(offender, OffenderDetail::class.java)
    return offenderDetail.otherIds.nomsNumber
  }
}

