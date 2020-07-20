package uk.gov.justice.digital.hmpps.indexer.service

import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient


@Service
class CommunityService(@Qualifier("communityApiWebClient") private val webClient: WebClient) {
  fun getOffender(crn: String) : Offender {
    return Offender(webClient.get()
        .uri("/secure/offenders/crn/${crn}/all")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()!!)
  }
  fun getCountAllOffenders() : OffendersPage {
    return webClient.get()
        .uri("/secure/offenders/primaryIdentifiers?includeDeleted=true&size=1")
        .retrieve()
        .bodyToMono(OffendersPage::class.java)
        .block()!!
  }
  fun getPageOfOffenders(page: Long, pageSize: Long) : OffendersPage {
    return webClient.get()
        .uri("/secure/offenders/primaryIdentifiers?includeDeleted=true&page={page}&size={pageSize}", page, pageSize)
        .retrieve()
        .bodyToMono(OffendersPage::class.java)
        .block()!!
  }
}

data class Offender(val body: String) {
  val detail : OffenderDetail = Gson().fromJson(body, OffenderDetail::class.java)
  val offenderId: Long
    get() {
      return detail.offenderId
    }
  val crn: String
    get() {
      return detail.crn
    }
}

data class OffenderDetail(val crn: String, val offenderId: Long, val otherIds: IDs? = null)
data class IDs(val pncNumber: String? = null, val croNumber: String? = null)
data class OffendersPage(val totalElements: Long, val numberOfElements: Long, val content: List<OffenderIdentifier>)
data class OffenderIdentifier(val crn: String)