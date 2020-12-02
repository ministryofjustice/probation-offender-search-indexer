package uk.gov.justice.digital.hmpps.indexer.config

import com.microsoft.applicationinsights.TelemetryConfiguration
import com.microsoft.applicationinsights.extensibility.TelemetryModule
import com.microsoft.applicationinsights.web.extensibility.modules.WebTelemetryModule
import com.microsoft.applicationinsights.web.internal.ThreadContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import java.text.ParseException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest

@Configuration
class ClientTrackingTelemetryModule : WebTelemetryModule, TelemetryModule {

  override fun onBeginRequest(req: ServletRequest, res: ServletResponse) {
    val properties = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
    val (user, clientId) = findUserAndClient(req)
    user?.let { properties["username"] = user }
    clientId?.let { properties["clientId"] = clientId }
  }

  private fun findUserAndClient(req: ServletRequest): Pair<String?, String?> =
    (req as HttpServletRequest).getHeader(HttpHeaders.AUTHORIZATION)
      .takeIf { it.startsWith("Bearer ") }
      ?.let { getClaimsFromJWT(it) }
      ?.let { it.getClaim("user_name") as String? to it.getClaim("client_id") as String? }
      ?: null to null

  private fun getClaimsFromJWT(token: String): JWTClaimsSet? =
    try {
      SignedJWT.parse(token.replace("Bearer ", ""))
    } catch (e: ParseException) {
      log.warn("problem decoding jwt public key for application insights", e)
      null
    }?.jwtClaimsSet

  override fun onEndRequest(req: ServletRequest, res: ServletResponse) {}
  override fun initialize(configuration: TelemetryConfiguration) {}

  companion object {
    val log: Logger = LoggerFactory.getLogger(ClientTrackingTelemetryModule::class.java)
  }
}
