package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import reactor.core.publisher.Mono

@Service
class CommunityService(@Qualifier("communityApiWebClient") private val webClient: WebClient) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getOffenderSearchDetails(crn: String): Either<OffenderError, Offender> =
    getOffender(crn).flatMap {
      return Offender(it, getOffenderMappa(crn)).right()
    }

  fun getOffender(crn: String): Either<OffenderError, String> =
    webClient.get()
      .uri("/secure/offenders/crn/{crn}/all", crn)
      .retrieve()
      .bodyToMono(String::class.java)
      .onErrorResume(::emptyIfNotFound)
      .block()
      ?.let { it.right() }
      ?: OffenderNotFoundError(crn).left()
        .also { log.error("Offender with crn {} not found", crn) }

  private fun emptyIfNotFound(exception: Throwable): Mono<out String> {
    return if (exception is NotFound) Mono.empty() else Mono.error(exception)
  }

  fun getOffenderMappa(crn: String): String? =
    webClient.get()
      .uri("/secure/offenders/crn/{crn}/risk/mappa", crn)
      .retrieve()
      .bodyToMono(String::class.java)
      .onErrorResume(NotFound::class.java) { emptyIfNotFound(it) }
      .block()

  fun getCountAllOffenders(): OffendersPage {
    return webClient.get()
      .uri("/secure/offenders/primaryIdentifiers?includeDeleted=true&size=1")
      .retrieve()
      .bodyToMono(OffendersPage::class.java)
      .block()!!
  }

  fun getPageOfOffenders(page: Long, pageSize: Long): OffendersPage {
    return webClient.get()
      .uri("/secure/offenders/primaryIdentifiers?includeDeleted=true&page={page}&size={pageSize}", page, pageSize)
      .retrieve()
      .bodyToMono(OffendersPage::class.java)
      .block()!!
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Offender(private val offenderJson: String, private val offenderMappaJson: String?) {
  private val detail: OffenderDetail = jacksonObjectMapper().readValue(offenderJson, OffenderDetail::class.java)
  val json = mergeJson(offenderJson, offenderMappaJson)
  val offenderId: Long
    get() {
      return detail.offenderId
    }
  val crn: String
    get() {
      return detail.otherIds.crn
    }
}

private fun mergeJson(offenderJson: String, offenderMappaJson: String?): String {
  val offenderMappaMap: MutableMap<String, Any?>? = offenderMappaJson?.let { jacksonObjectMapper().readValue(offenderMappaJson) }
  val offenderMap: MutableMap<String, Any?> = offenderJson.let { jacksonObjectMapper().readValue(offenderJson) }
  return (offenderMap + ("mappa" to offenderMappaMap)).let { jacksonObjectMapper().writeValueAsString(it) }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class OffenderDetail(val offenderId: Long, val otherIds: IDs)
@JsonIgnoreProperties(ignoreUnknown = true)
data class IDs(val crn: String, val pncNumber: String? = null, val croNumber: String? = null, val nomsNumber: String? = null)
data class OffendersPage(val totalElements: Long, val numberOfElements: Long, val content: List<OffenderIdentifier>)
data class OffenderIdentifier(val crn: String)
