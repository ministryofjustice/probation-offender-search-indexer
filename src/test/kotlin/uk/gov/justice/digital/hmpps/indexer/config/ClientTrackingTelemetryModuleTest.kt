package uk.gov.justice.digital.hmpps.indexer.config

import ch.qos.logback.classic.Level
import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext
import com.microsoft.applicationinsights.web.internal.ThreadContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.indexer.helpers.JwtAuthHelper
import uk.gov.justice.digital.hmpps.indexer.helpers.findLogAppender

@Import(JwtAuthHelper::class, ClientTrackingTelemetryModule::class, JwtConfig::class)
@ContextConfiguration(initializers = [ConfigFileApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class ClientTrackingTelemetryModuleTest {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var clientTrackingTelemetryModule: ClientTrackingTelemetryModule

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var jwtAuthHelper: JwtAuthHelper

  @BeforeEach
  fun setup() {
    ThreadContext.setRequestTelemetryContext(RequestTelemetryContext(1L))
  }

  @AfterEach
  fun tearDown() {
    ThreadContext.remove()
  }

  @Test
  fun `should add clientId and userName to insight telemetry`() {
    val token = jwtAuthHelper.createJwt("bob")
    val req = MockHttpServletRequest()
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
    val res = MockHttpServletResponse()

    clientTrackingTelemetryModule.onBeginRequest(req, res)

    val insightTelemetry = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
    assertThat(insightTelemetry).hasSize(2)
    assertThat(insightTelemetry["username"]).isEqualTo("bob")
    assertThat(insightTelemetry["clientId"]).isEqualTo("prisoner-offender-search-client")
  }

  @Test
  fun `should add only clientId if username null to insight telemetry`() {
    val token = jwtAuthHelper.createJwt(null)
    val req = MockHttpServletRequest()
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
    val res = MockHttpServletResponse()

    clientTrackingTelemetryModule.onBeginRequest(req, res)

    val insightTelemetry = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
    assertThat(insightTelemetry).hasSize(1)
    assertThat(insightTelemetry["clientId"]).isEqualTo("prisoner-offender-search-client")
  }

  @Test
  fun `should allow bad JwtToken and log a warning, but cannot send clientId or username to insigh telemetry`() {
    val token = "This is not a valid token"
    val req = MockHttpServletRequest()
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
    val res = MockHttpServletResponse()
    val logAppender = findLogAppender(ClientTrackingTelemetryModule::class.java)

    clientTrackingTelemetryModule.onBeginRequest(req, res)

    // The lack of an exception here shows that a bad token does not prevent Telemetry
    val insightTelemetry = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
    assertThat(insightTelemetry).hasSize(0)
    assertThat(logAppender.list).anyMatch { it.message.contains("problem decoding jwt") && it.level == Level.WARN }
  }
}
