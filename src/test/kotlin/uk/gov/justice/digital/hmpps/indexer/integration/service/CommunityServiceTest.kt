package uk.gov.justice.digital.hmpps.indexer.integration.service

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.service.CommunityService
import uk.gov.justice.digital.hmpps.indexer.service.OffenderNotFoundError
import java.net.HttpURLConnection

internal class CommunityServiceTest : IntegrationTestBase() {
  @Autowired
  private lateinit var service: CommunityService

  @BeforeEach
  internal fun setUp() {
    CommunityApiExtension.communityApi.resetMappings()
  }

  @Nested
  inner class GetOffender {
    @Test
    fun `will get offender using the crn `() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            {
              "offenderId": 99,
              "otherIds": {
                "crn": "X12345"
              }
            }
            """
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      service.getOffender("X12345")

      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/all"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will return an offender whose JSON can be retrieved as a String`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
            "offenderId": 99,
            "otherIds": {
              "crn": "X12345"
            }
          }"""
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      val offender = service.getOffender("X12345").right()

      offender shouldBeRight {
        it.map { offenderJson -> assertThat(offenderJson).contains(""""crn": "X12345"""") }
      }
    }

    @Test
    fun `a 404 not found is an expected error`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\": \"not found\"}")
            .withStatus(HttpURLConnection.HTTP_NOT_FOUND)
        )
      )

      val result = service.getOffender("X12345")

      result shouldBeLeft OffenderNotFoundError("X12345")
    }
  }

  @Nested
  inner class GetOffenderMappa {

    @Test
    fun `will get offender MAPPA using the crn `() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(someMappaDetailsJson())
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      val offenderMappaDetail = service.getOffenderMappa("X12345")
        .block()

      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/risk/mappa"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
      assertThat(offenderMappaDetail).isNotNull
      assertThat(offenderMappaDetail).isNotEmpty
    }

    @Test
    fun `will return null if not found`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_NOT_FOUND)
        )
      )

      val offenderMappaDetail = service.getOffenderMappa("X12345")
        .block()

      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/risk/mappa"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
      assertThat(offenderMappaDetail).isNull()
    }

    @Test
    fun `will throw any other exception`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy {
        service.getOffenderMappa("X12345")
          .block()
      }.isInstanceOf(WebClientResponseException::class.java)

      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/risk/mappa"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }
  }

  @Nested
  inner class GetOffenderProbationStatus {

    @Test
    fun `will get offender probationStatus using the crn`() {
      val json = someProbationStatusJson()
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.urlEqualTo("/secure/offenders/crn/X12345/probationStatus")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              json
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      val offenderProbationStatus = service.getOffenderProbationStatus("X12345").block()

      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/probationStatus"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
      assertThat(offenderProbationStatus).isNotEmpty
      assertThat(offenderProbationStatus).isEqualTo(json)
    }

    @Test
    fun `will throw any exception other than not found`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_BAD_REQUEST)
        )
      )

      assertThatThrownBy { service.getOffenderProbationStatus("X12345").block() }.isInstanceOf(WebClientResponseException::class.java)

      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/probationStatus"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will return null if not found`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_NOT_FOUND)
        )
      )

      val probationStatus = service.getOffenderProbationStatus("X12345")
        .block()

      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/probationStatus"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
      assertThat(probationStatus).isNull()
    }
  }

  @Nested
  inner class GetOffenderSearchDetails {
    @Test
    fun `can retrieve both offender details, probation status and mappa`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.urlEqualTo("/secure/offenders/crn/X12345/all")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
            "offenderId": 99,
            "otherIds": {
              "crn": "X12345"
            }
          }"""
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.urlEqualTo("/secure/offenders/crn/X12345/probationStatus")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              someProbationStatusJson()
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.urlEqualTo("/secure/offenders/crn/X12345/risk/mappa")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(someMappaDetailsJson())
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )
      val result = service.getOffenderSearchDetails("X12345")

      result shouldBeRight {
        assertThatJson(it.json).node("offenderId").isEqualTo(99)
        assertThatJson(it.json).node("otherIds.crn").isEqualTo("X12345")
        assertThatJson(it.json).node("mappa.level").isEqualTo(1)
        assertThatJson(it.json).node("mappa.team.code").isEqualTo("N02AAM")
        assertThatJson(it.json).node("probationStatus.status").isEqualTo("CURRENT")
        assertThatJson(it.json).node("probationStatus.inBreach").isEqualTo(true)
        assertThatJson(it.json).node("probationStatus.preSentenceActivity").isEqualTo(false)
      }
    }

    @Test
    fun `can retrieve offender details and probation status without mappa`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.urlEqualTo("/secure/offenders/crn/X12345/all")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
            "offenderId": 99,
            "otherIds": {
              "crn": "X12345"
            }
          }"""
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.urlEqualTo("/secure/offenders/crn/X12345/probationStatus")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              someProbationStatusJson()
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.urlEqualTo("/secure/offenders/crn/X12345/risk/mappa")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_NOT_FOUND)
        )
      )
      val result = service.getOffenderSearchDetails("X12345")

      result shouldBeRight {
        assertThatJson(it.json).node("offenderId").isEqualTo(99)
        assertThatJson(it.json).node("otherIds.crn").isEqualTo("X12345")
        assertThatJson(it.json).node("mappa").isNull()
        assertThatJson(it.json).node("probationStatus.status").isEqualTo("CURRENT")
        assertThatJson(it.json).node("probationStatus.inBreach").isEqualTo(true)
        assertThatJson(it.json).node("probationStatus.preSentenceActivity").isEqualTo(false)
      }
    }

    @Test
    fun `will return an offender whose ID can be retrieved`() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
            "offenderId": 99,
            "otherIds": {
              "crn": "X12345"
            }
          }"""
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )

      val offender = service.getOffenderSearchDetails("X12345")

      offender shouldBeRight { assertThat(it.crn).isEqualTo("X12345") }
    }
  }

  @Nested
  inner class GetCountAllOffenders {
    @BeforeEach
    internal fun setUp() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
          {
              "content": [
                  {
                      "offenderId": 2500000501,
                      "crn": "D001022"
                  }
              ],
              "pageable": {
                  "sort": {
                      "sorted": true,
                      "unsorted": false,
                      "empty": false
                  },
                  "offset": 0,
                  "pageNumber": 0,
                  "pageSize": 1,
                  "paged": true,
                  "unpaged": false
              },
              "totalPages": 339667,
              "totalElements": 339667,
              "last": false,
              "sort": {
                  "sorted": true,
                  "unsorted": false,
                  "empty": false
              },
              "first": true,
              "number": 0,
              "size": 1,
              "numberOfElements": 1,
              "empty": false
          }            
          """
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )
    }

    @Test
    internal fun `will securely request primary identifiers for all offenders`() {
      service.getCountAllOffenders()
      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `will request deleted offenders`() {
      service.getCountAllOffenders()
      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withQueryParam("includeDeleted", WireMock.equalTo("true"))
      )
    }

    @Test
    internal fun `will request smallest page size since we only care about the total count`() {
      service.getCountAllOffenders()
      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withQueryParam("size", WireMock.equalTo("1"))
      )
    }

    @Test
    internal fun `will return a page that will contain count of all offenders`() {

      val offenders = service.getCountAllOffenders()

      assertThat(offenders.totalElements).isEqualTo(339667)
    }
  }

  @Nested
  inner class GetPageOfOffenders {
    @BeforeEach
    internal fun setUp() {
      CommunityApiExtension.communityApi.stubFor(
        WireMock.get(WireMock.anyUrl()).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
            "content": [
                {
                    "offenderId": 2500000501,
                    "crn": "D001022"
                },
                {
                    "offenderId": 2500000502,
                    "crn": "D001023"
                },
                {
                    "offenderId": 2500000503,
                    "crn": "D001024"
                },
                {
                    "offenderId": 2500000504,
                    "crn": "D001025"
                },
                {
                    "offenderId": 2500000505,
                    "crn": "D001026"
                },
                {
                    "offenderId": 2500000506,
                    "crn": "D001027"
                },
                {
                    "offenderId": 2500000507,
                    "crn": "D001028"
                },
                {
                    "offenderId": 2500000508,
                    "crn": "D001029"
                },
                {
                    "offenderId": 2500000509,
                    "crn": "D001030"
                },
                {
                    "offenderId": 2500000510,
                    "crn": "D001031"
                }
            ],
            "pageable": {
                "sort": {
                    "sorted": true,
                    "unsorted": false,
                    "empty": false
                },
                "offset": 0,
                "pageNumber": 0,
                "pageSize": 10,
                "paged": true,
                "unpaged": false
            },
            "totalPages": 33969,
            "totalElements": 339682,
            "last": false,
            "sort": {
                "sorted": true,
                "unsorted": false,
                "empty": false
            },
            "first": true,
            "number": 0,
            "size": 10,
            "numberOfElements": 10,
            "empty": false
        }"""
            )
            .withStatus(HttpURLConnection.HTTP_OK)
        )
      )
    }

    @Test
    internal fun `will securely request primary identifiers for all offenders in the page`() {
      service.getPageOfOffenders(0, 10)
      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `will request deleted offenders`() {
      service.getPageOfOffenders(0, 10)
      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withQueryParam("includeDeleted", WireMock.equalTo("true"))
      )
    }

    @Test
    internal fun `will request the supplied page number`() {
      service.getPageOfOffenders(0, 10)
      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withQueryParam("page", WireMock.equalTo("0"))
      )
    }

    @Test
    internal fun `will request the supplied page size`() {
      service.getPageOfOffenders(0, 10)
      CommunityApiExtension.communityApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withQueryParam("size", WireMock.equalTo("10"))
      )
    }

    @Test
    internal fun `will return a page of offenders`() {
      val offenders = service.getPageOfOffenders(0, 10)

      assertThat(offenders.content).hasSize(10)
    }
  }

  private fun someProbationStatusJson() = """{
                  "status": "CURRENT",
                  "inBreach": true,
                  "preSentenceActivity": false
              }
  """.trimIndent()

  private fun someMappaDetailsJson(): String =
    """{
         "level": 1,
         "levelDescription": "MAPPA Level 1",
         "category": 2,
         "categoryDescription": "MAPPA Category 2",
         "startDate": "2021-02-08",
         "reviewDate": "2021-05-08",
         "team": {"code": "N02AAM", "description": "OMIC OMU A"},
         "officer": {"code:": "N02AAMU", "forenames": "Unallocated", "surname": "Staff"},
         "probationArea": {"code": "N02", "description": "NPS London"},
         "notes": "Registered level 1 cat 2"
       }
    """.trimIndent()
}
