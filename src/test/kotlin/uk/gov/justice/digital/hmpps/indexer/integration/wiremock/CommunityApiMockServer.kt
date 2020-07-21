package uk.gov.justice.digital.hmpps.indexer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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

  fun stubHealthPing(status: Int) {
    stubFor(get("/health/ping").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(if (status == 200) "pong" else "some error")
        .withStatus(status)))

  }

  fun stubAllOffenders(count: Long) {
    CommunityApiExtension.communityApi.stubFor(
        get(urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
            .withQueryParam("size", WireMock.equalTo("1"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
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
                """)
                .withStatus(HttpURLConnection.HTTP_OK)))
  }

  fun stubPageOfOffenders(crns: List<String>) {
    val offenders = crns.map { mapOf("offenderId" to Random(1).nextInt(), "crn" to it) }
    val offenderList = Gson().toJson(offenders)

    CommunityApiExtension.communityApi.stubFor(
        get(urlPathEqualTo("/secure/offenders/primaryIdentifiers"))
            .withQueryParam("size", WireMock.equalTo("1000"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
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
                """)
                .withStatus(HttpURLConnection.HTTP_OK)))
  }

  fun getCountFor(url: String) = CommunityApiExtension.communityApi.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
