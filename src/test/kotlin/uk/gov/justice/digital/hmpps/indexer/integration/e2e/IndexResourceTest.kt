package uk.gov.justice.digital.hmpps.indexer.integration.e2e

import com.amazonaws.services.sqs.model.PurgeQueueRequest
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
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.OffenderAlias
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.OffenderManager
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.ProbationArea
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

      @Nested
      inner class CROMapping {
        @BeforeEach
        fun setup() {
          deleteOffenderIndexes()
          initialiseIndexStatus()
          CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346", "X12347")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12345", croNumber = "46189/08G")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12346", croNumber = "46189/99G")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12347", croNumber = "99999/08G")
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
          deleteOffenderIndexes()
          initialiseIndexStatus()
          CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12345", offenderManagers = listOf(
              OffenderManager(probationArea = ProbationArea(code = "N01"), active = true),
              OffenderManager(probationArea = ProbationArea(code = "N02"), active = false)
          ))
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12346", offenderManagers = listOf(
              OffenderManager(probationArea = ProbationArea(code = "N01"), active = false),
              OffenderManager(probationArea = ProbationArea(code = "N02"), active = true)
          ))
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
          deleteOffenderIndexes()
          initialiseIndexStatus()
          CommunityApiExtension.communityApi.stubAllOffenderGets(10, "X12345", "X12346")
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12345", offenderAliases = listOf(
              OffenderAlias(surname = "Smith", firstName = "Jane", gender = "FEMALE", dateOfBirth = "1965-07-19"),
              OffenderAlias(surname = "Kunis", firstName = "Ella", gender = "FEMALE", dateOfBirth = "1965-07-19")
          ))
          CommunityApiExtension.communityApi.stubGetOffender(crn = "X12346", offenderAliases = listOf(
              OffenderAlias(surname = "Smith", firstName = "Ella", gender = "FEMALE", dateOfBirth = "1965-07-19"),
              OffenderAlias(surname = "Kunis", firstName = "Jane", gender = "FEMALE", dateOfBirth = "1965-07-19")
          ))
          buildAndSwitchIndex(GREEN, 2)
        }

        @Test
        internal fun `legacy applications can still use a non-nested search though not recommended`() {
          val query = QueryBuilders.boolQuery()
          query.must(QueryBuilders.matchQuery("offenderAliases.surname", "Smith"))
          query.must(QueryBuilders.matchQuery("offenderAliases.firstName", "Jane"))
          val results = search(query)

          assertThat(results.hits.asList()).extracting<String> { it.id }.containsExactly("X12345", "X12346")
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
          indexAwsSqsClient.sendMessage(indexDlqUrl, "{}")
        }
        await untilCallTo { getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 10 }
      }

      @Test
      internal fun `will remove all message on index DLQ`() {
        webTestClient.put()
            .uri("/probation-index/purge-index-dlq")
            .accept(MediaType.APPLICATION_JSON)
            .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
            .exchange()
            .expectStatus().isOk

        await untilCallTo { getNumberOfMessagesCurrentlyOnIndexDLQ() } matches { it == 0 }
      }
    }

    @Nested
    inner class EventDLQ {
      @BeforeEach
      internal fun setUp() {
        repeat(10) {
          eventAwsSqsClient.sendMessage(eventDlqUrl, "{}")
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


  fun nomsNumberOf(crn: String): String? {
    val offender = getById(index = "offender", crn = crn)
    val offenderDetail = gson.fromJson<OffenderDetail>(offender, OffenderDetail::class.java)
    return offenderDetail.otherIds.nomsNumber
  }
}

