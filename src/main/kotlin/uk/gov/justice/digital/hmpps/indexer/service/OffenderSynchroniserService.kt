package uk.gov.justice.digital.hmpps.indexer.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository

@Service
class OffenderSynchroniserService(val communityService: CommunityService, val offenderRepository: OffenderRepository, val indexStatusService: IndexStatusService, @Value("\${index.page.size:1000}") private val pageSize: Long) {
  fun synchroniseOffender(crn: String, index: SyncIndex): String {
    val offender = communityService.getOffender(crn)
    offenderRepository.save(offender, indexStatusService.getIndexStatus().currentIndex)
    return offender.json
  }

  fun checkExistsAndReset(index: SyncIndex) {
    if (offenderRepository.doesIndexExist(index)) {
      offenderRepository.deleteIndex(index)
    }
    offenderRepository.createIndex(index)
  }

  fun splitAllOffendersIntoChunks(): List<OffenderPage> {
    val totalNumberOfOffenders = communityService.getCountAllOffenders().totalElements
    return (1..totalNumberOfOffenders step pageSize).asSequence().toList()
        .map { OffenderPage(it / pageSize, pageSize) }
  }

  fun getAllOffenderIdentifiersInPage(offenderPage: OffenderPage): List<OffenderIdentifier> {
    return communityService.getPageOfOffenders(offenderPage.page, offenderPage.pageSize)
        .content.map { OffenderIdentifier(it.crn) }
  }

}

data class OffenderPage(val page: Long, val pageSize: Long)