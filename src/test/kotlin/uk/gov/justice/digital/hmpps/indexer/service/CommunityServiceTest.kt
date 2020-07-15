package uk.gov.justice.digital.hmpps.indexer.service

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import java.net.HttpURLConnection

internal class CommunityServiceTest : IntegrationTest() {
  @Autowired
  private lateinit var service: CommunityService

  @Test
  fun `will get offender using the crn `() {
    CommunityApiExtension.communityApi.stubFor(WireMock.get(WireMock.anyUrl()).willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("""{
          "offenderId": 99
          "crn": "X12345"
          }""")
        .withStatus(HttpURLConnection.HTTP_OK)))

    service.getOffender("X12345")

    CommunityApiExtension.communityApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/secure/offenders/crn/X12345/all"))
        .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")))

  }

  @Test
  fun `will return an offender whose JSON can be retrieved as a String`() {
    CommunityApiExtension.communityApi.stubFor(WireMock.get(WireMock.anyUrl()).willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("""{
          "offenderId": 99,
          "crn": "X12345"
          }""")
        .withStatus(HttpURLConnection.HTTP_OK)))

    val offender = service.getOffender("X12345")


    assertThat(offender.body).contains("\"offenderId\": 99")
  }

  @Test
  fun `will return an offender whose ID can be retrieved`() {
    CommunityApiExtension.communityApi.stubFor(WireMock.get(WireMock.anyUrl()).willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("""{
          "offenderId": 99,
          "crn": "X12345"
          }""")
        .withStatus(HttpURLConnection.HTTP_OK)))

    val offender = service.getOffender("X12345")


    assertThat(offender.offenderId).isEqualTo(99)
  }

  @Test
  fun `a 404 not found is treated as an unexpected error`() {
    CommunityApiExtension.communityApi.stubFor(WireMock.get(WireMock.anyUrl()).willReturn(WireMock.aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody("{\"error\": \"not found\"}")
        .withStatus(HttpURLConnection.HTTP_NOT_FOUND)))

    assertThatThrownBy {
      service.getOffender("X12345")
    }.hasMessageContaining("404 Not Found")
  }
}