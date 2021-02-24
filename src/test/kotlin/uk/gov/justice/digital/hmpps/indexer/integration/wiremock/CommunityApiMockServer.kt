package uk.gov.justice.digital.hmpps.indexer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.google.gson.Gson
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.net.HttpURLConnection
import kotlin.random.Random

class CommunityApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val communityApi = CommunityApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    communityApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    communityApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    communityApi.stop()
  }
}

class CommunityApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8096
  }

  fun getCountFor(url: String) = CommunityApiExtension.communityApi.findAll(getRequestedFor(urlEqualTo(url))).count()

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  fun stubGetOffender(
    crn: String = "X123456",
    nomsNumber: String = "A1234BC",
    pncNumber: String? = null,
    croNumber: String? = null,
    offenderManagers: List<OffenderManager> = listOf(OffenderManager(true, ProbationArea("N02"))),
    offenderAliases: List<OffenderAlias> = listOf()
  ): StubMapping =
    stubFor(
      get("/secure/offenders/crn/$crn/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            anOffenderDetail(
              crn = crn,
              nomsNumber = nomsNumber,
              pncNumber = pncNumber,
              croNumber = croNumber,
              offenderManagers = offenderManagers,
              offenderAliases = offenderAliases
            )
          )
          .withStatus(200)
      )
    )

  fun stubOffenderNotFound(crn: String): StubMapping =
    stubFor(
      get("/secure/offenders/crn/$crn/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(404)
      )
    )

  fun stubGetMappaDetails(
    crn: String = "X123456",
    level: Int = 1,
    teamCode: String = "N02AAM",
    notes: String = "Mappa Details"
  ): StubMapping =
    stubFor(
      get("/secure/offenders/crn/$crn/risk/mappa").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(aMappaDetails(level, teamCode, notes))
          .withStatus(200)
      )
    )

  fun stubMappaNotFound(crn: String): StubMapping =
    stubFor(
      get("/secure/offenders/crn/$crn/risk/mappa").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(404)
      )
    )

  fun verifyGetOffender(crn: String = "X123456") =
    verify(
      getRequestedFor(urlEqualTo("/secure/offenders/crn/$crn/all"))
        .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
    )

  fun verifyNotGetOffender(crn: String = "X123456") =
    verify(
      0,
      getRequestedFor(urlEqualTo("/secure/offenders/crn/$crn/all"))
        .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
    )

  fun stubAllOffenderGets(pageSize: Long = 1000L, vararg crns: String) {
    stubAllOffenders(crns.size.toLong())
    stubPageOfOffenders(pageSize, *crns)
    crns.forEach {
      stubGetOffender(it)
      stubMappaNotFound(it)
    }
  }

  fun stubAllOffenderGets(pageSize: Long = 1000L, numberOfOffenders: Long = 1L) {
    val crns = (1..numberOfOffenders).asSequence().map { "X%05d".format(it) }.toList().toTypedArray()
    stubAllOffenderGets(pageSize, *crns)
  }


  fun stubGetProbationStatus(
    crn: String = "X123456"
  ): StubMapping =
    stubFor(
      get("/secure/offenders/crn/$crn/probationStatus").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""
            {
                "status": "CURRENT",
                "inBreach": true,
                "preSentenceActivity": false
            }
          """.trimIndent())
          .withStatus(200)
      )
    )

  private fun anOffenderDetail(
    offenderId: Long = 490001467,
    crn: String = "X123456",
    nomsNumber: String = "A1234BC",
    pncNumber: String? = null,
    croNumber: String? = null,
    offenderManagers: List<OffenderManager> = listOf(OffenderManager(true, ProbationArea("N02"))),
    offenderAliases: List<OffenderAlias> = listOf()
  ): String =
    """
{
  "gender": "Male",
  "dateOfBirth": "1965-07-19",
  "title": "Mr",
  "softDeleted": false,
  "contactDetails": {
    "addresses": [
      {
        "noFixedAbode": false,
        "streetName": "23 BLOOD ROAD",
        "telephoneNumber": "0777 555 5555",
        "town": "SHEFFIELD",
        "county": "SOUTH YORKS",
        "postcode": "S1 2BJ",
        "from": "2011-01-11",
        "to": "2016-07-22",
        "status": {
          "code": "P",
          "description": "Previous"
        }
      },
      {
        "noFixedAbode": true,
        "notes": "NFA on release",
        "postcode": "NF1 1NF",
        "from": "2016-07-22",
        "to": "2017-03-22",
        "status": {
          "code": "P",
          "description": "Previous"
        }
      },
      {
        "noFixedAbode": false,
        "streetName": "8 Ripon Lane",
        "telephoneNumber": "07393555555",
        "town": "Rotherham",
        "addressNumber": "Flat 22,",
        "county": "South Yorkshire",
        "postcode": "S29 1TT",
        "from": "2017-03-22",
        "status": {
          "code": "M",
          "description": "Main"
        }
      }
    ],
    "phoneNumbers": [
      {
        "number": "07393555555",
        "type": "MOBILE"
      }
    ]
  },
  "firstName": "John",
  "currentRestriction": false,
  "offenderManagers": ${Gson().toJson(offenderManagers)},
  "surname": "Smith",
  "partitionArea": "Yorkshire",
  "otherIds": {
    "nomsNumber": "$nomsNumber",
    "pncNumber": "$pncNumber",
    "croNumber": "$croNumber",
    "crn": "$crn"
  },
  "offenderAliases": ${Gson().toJson(offenderAliases)},
  "offenderId": $offenderId,
  "currentDisposal": "0",
  "currentExclusion": false,
  "offenderProfile": {
    "riskColour": "Green",
    "offenderDetails": "Super cool",
    "disabilities": [
      {
        "disabilityType": {
          "code": "RM",
          "description": "Reduced Mobility"
        },
        "notes": "Walking issue",
        "startDate": "2013-04-11",
        "disabilityId": 490017999
      },
      {
        "disabilityType": {
          "code": "MI",
          "description": "Mental Illness"
        },
        "notes": "Worries all of the time",
        "startDate": "2017-03-11",
        "disabilityId": 490015999
      },
      {
        "disabilityType": {
          "code": "OD",
          "description": "Other"
        },
        "notes": "Can not read",
        "startDate": "2015-01-15",
        "disabilityId": 490009999
      }
    ],
    "ethnicity": "White: British/English/Welsh/Scottish/Northern Irish",
    "nationality": "British",
    "offenderLanguages": {},
    "immigrationStatus": "UK National",
    "previousConviction": {
      "convictionDate": "2015-08-18",
      "detail": {
        "documentName": "pre cons 22.04.16.pdf"
      }
    },
    "religion": "Christian"
  }
} 
    """.trimIndent()
      .replace("\"pncNumber\": \"null\",", "")
      .replace("\"croNumber\": \"null\",", "")

  private fun aMappaDetails(
    level: Int = 1,
    teamCode: String = "N02AAM",
    notes: String = "Mappa Details"
  ): String =
    """{
         "level": $level,
         "levelDescription": "MAPPA Level $level",
         "category": 2,
         "categoryDescription": "MAPPA Category 2",
         "startDate": "2021-02-08",
         "reviewDate": "2021-05-08",
         "team": {"code": "$teamCode", "description": "OMIC OMU A"},
         "officer": {"code:": "N02AAMU", "forenames": "Unallocated", "surname": "Staff"},
         "probationArea": {"code": "N02", "description": "NPS London"},
         "notes": "$notes"
       }
    """.trimIndent()

  private fun stubAllOffenders(count: Long) {
    CommunityApiExtension.communityApi.stubFor(
      get(urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
        .withQueryParam("size", WireMock.equalTo("1"))
        .withQueryParam("includeDeleted", WireMock.equalTo("true"))
        .willReturn(
          aResponse()
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
                    "totalPages": $count,
                    "totalElements": $count,
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

  private fun stubPageOfOffenders(pageSize: Long = 1000, vararg crns: String) {
    // group CRNs for each page
    val pagesOfCrns = listOf(*crns).withIndex().groupBy { it.index / pageSize }
      .map { it.value.map { crn -> crn.value } }
    pagesOfCrns.withIndex().forEach {
      val offenders = it.value.map { crn -> mapOf("offenderId" to Random(1).nextInt(), "crn" to crn) }
      val offenderList = Gson().toJson(offenders)

      CommunityApiExtension.communityApi.stubFor(
        get(urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
          .withQueryParam("size", WireMock.equalTo(pageSize.toString()))
          .withQueryParam("page", WireMock.equalTo(it.index.toString()))
          .withQueryParam("includeDeleted", WireMock.equalTo("true"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                {
                    "content": $offenderList,
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
                    "totalPages": 20000,
                    "totalElements": 2000000,
                    "last": false,
                    "sort": {
                        "sorted": true,
                        "unsorted": false,
                        "empty": false
                    },
                    "first": true,
                    "number": 0,
                    "size": ${crns.size},
                    "numberOfElements": 1,
                    "empty": false
                }            
                """
              )
              .withStatus(HttpURLConnection.HTTP_OK)
          )
      )
    }
  }
}

data class ProbationArea(val code: String)
data class OffenderManager(val active: Boolean, val probationArea: ProbationArea)
data class OffenderAlias(val firstName: String, val surname: String, val dateOfBirth: String, val gender: String)
