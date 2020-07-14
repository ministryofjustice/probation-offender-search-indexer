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
}


data class Offender(val body: String) {


  val offenderId: Long
    get() {
      val detail  = Gson().fromJson(body, OffenderDetail::class.java)
      return detail.offenderId
    }
}

data class OffenderDetail(val crn: String, val offenderId: Long)