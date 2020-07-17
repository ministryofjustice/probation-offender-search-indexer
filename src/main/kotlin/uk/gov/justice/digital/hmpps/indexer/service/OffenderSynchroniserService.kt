package uk.gov.justice.digital.hmpps.indexer.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class OffenderSynchroniserService(val communityService: CommunityService, val offenderRepository: OffenderRepository, val indexStatusService: IndexStatusService) {
  fun synchroniseOffender(crn: String) : String {
    val offender = communityService.getOffender(crn)
    offenderRepository.save(offender, indexStatusService.getIndexStatus().currentIndex)
    return offender.body
  }

  fun checkExistsAndReset(index: SyncIndex) {
    if (offenderRepository.doesIndexExist(index)) {
      offenderRepository.deleteIndex(index)
    }
    offenderRepository.createIndex(index)
  }

  fun splitAllOffendersIntoChunks(): List<OffenderPage> {
    return listOf(OffenderPage(1, 1000))
  }

}

data class OffenderPage(val page: Int, val pageSize: Int)