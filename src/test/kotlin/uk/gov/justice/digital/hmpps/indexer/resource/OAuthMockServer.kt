package uk.gov.justice.digital.hmpps.indexer.resource

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class OAuthExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val oAuthApi = OAuthMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    oAuthApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    oAuthApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    oAuthApi.stop()
  }
}

class OAuthMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8998
  }

  fun stubGrantToken() {
    stubFor(
        WireMock.post(WireMock.urlEqualTo("/auth/oauth/token"))
            .willReturn(WireMock.aResponse()
                .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
                .withBody("""{
                    "token_type": "bearer",
                    "access_token": "ABCDE"
                }""".trimIndent()))
    )
  }

}
