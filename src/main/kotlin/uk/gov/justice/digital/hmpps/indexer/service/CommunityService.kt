package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException


@Service
class CommunityService(@Qualifier("communityApiWebClient") private val webClient: WebClient) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getOffender(crn: String): Either<OffenderError, Offender> =
    try {
      webClient.get()
          .uri("/secure/offenders/crn/${crn}/all")
          .retrieve()
          .bodyToMono(String::class.java)
          .block()!!
          .let { Offender(it).right() }
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        log.error("Failed to retrieve offender with crn {}", crn)
        OffenderNotFoundError(crn).left()
      } else {
        throw e
      }
    }

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

data class Offender(val json: String) {
  private val detail: OffenderDetail = Gson().fromJson(json, OffenderDetail::class.java)
  val offenderId: Long
    get() {
      return detail.offenderId
    }
  val crn: String
    get() {
      return detail.otherIds.crn
    }
}

data class OffenderDetail(val offenderId: Long, val otherIds: IDs)
data class IDs(val crn: String, val pncNumber: String? = null, val croNumber: String? = null, val nomsNumber: String? = null)
data class OffendersPage(val totalElements: Long, val numberOfElements: Long, val content: List<OffenderIdentifier>)
data class OffenderIdentifier(val crn: String)