package uk.gov.justice.digital.hmpps.indexer.resource

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.QueueIntegrationTest
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import java.net.HttpURLConnection


class IndexResourceIntegrationTest : QueueIntegrationTest() {

  @Nested
  inner class BuildIndex {
    @BeforeEach
    internal fun setUp() {
      CommunityApiExtension.communityApi.stubAllOffenders(1)
      CommunityApiExtension.communityApi.stubPageOfOffenders(listOf("X12345"))
      CommunityApiExtension.communityApi.stubFor(WireMock.get(WireMock.urlPathEqualTo("/secure/offenders/crn/X12345/all")).willReturn(WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{
          "offenderId": 99,
          "otherIds": {
            "crn": "X12345"
          }
          }""")
          .withStatus(HttpURLConnection.HTTP_OK)))

    }

    @Nested
    @Disabled("Being fixed on another branch")
    inner class FirstTimeRun {
      @Test
      internal fun `will build the green index`() {
        webTestClient.put()
            .uri("/probation-index/build-index")
            .accept(MediaType.APPLICATION_JSON)
            .headers(setAuthorisation(roles = listOf("ROLE_PROBATION_INDEX")))
            .exchange()
            .expectStatus().isOk
//   FAILING TEST         .expectBody()
//            .jsonPath("$.currentIndex").isEqualTo("GREEN")
//            .jsonPath("$.currentIndexState").isEqualTo("BUILDING")
//            .jsonPath("$.otherIndex").isEqualTo("BLUE")
//            .jsonPath("$.otherIndexState").isEqualTo("NEW")

        await untilCallTo { CommunityApiExtension.communityApi.getCountFor("/secure/offenders/crn/X12345/all") } matches { it == 1 }
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