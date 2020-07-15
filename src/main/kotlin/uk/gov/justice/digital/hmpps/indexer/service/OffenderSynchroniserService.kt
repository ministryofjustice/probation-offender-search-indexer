package uk.gov.justice.digital.hmpps.indexer.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class OffenderSynchroniserService(val communityService: CommunityService, val offenderRepository: OffenderRepository, val indexStatusService: IndexStatusService) {
  fun synchroniseOffender(crn: String) : String {
    val offender = communityService.getOffender(crn)
    offenderRepository.save(offender, indexStatusService.getOrCreateCurrentIndexStatus().currentIndex)
    return offender.body
  }

  fun checkExistsAndReset(syncIndex: SyncIndex) {}

}