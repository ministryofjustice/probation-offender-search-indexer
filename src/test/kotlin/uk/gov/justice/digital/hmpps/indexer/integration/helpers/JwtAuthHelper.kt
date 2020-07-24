package uk.gov.justice.digital.hmpps.indexer.integration.helpers

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import java.security.KeyPair
import java.time.Duration
import java.util.*
import kotlin.collections.HashMap

@Component
class JwtAuthHelper(private val keyPair: KeyPair) {

  fun setAuthorisation(user: String = "probation-offender-search-indexer-client", roles: List<String> = listOf()): (HttpHeaders) -> Unit {
    val token = createJwt(subject = user,
        scope = listOf("read"),
        expiryTime = Duration.ofHours(1L),
        roles = roles)
    return { it.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
  }

  private fun createJwt(subject: String,
                scope: List<String>? = listOf(),
                roles: List<String>? = listOf(),
                expiryTime: Duration = Duration.ofHours(1),
                jwtId: String = UUID.randomUUID().toString()): String {
    val claims = HashMap<String, Any>()
    claims["user_name"] = subject
    claims["client_id"] = "prisoner-offender-search-client"
    if (!roles.isNullOrEmpty()) claims["authorities"] = roles
    if (!scope.isNullOrEmpty()) claims["scope"] = scope
    return Jwts.builder()
        .setId(jwtId)
        .setSubject(subject)
        .addClaims(claims)
        .setExpiration(Date(System.currentTimeMillis() + expiryTime.toMillis()))
        .signWith(SignatureAlgorithm.RS256, keyPair.private)
        .compact()
  }
}
