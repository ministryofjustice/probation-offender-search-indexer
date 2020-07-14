package uk.gov.justice.digital.hmpps.indexer.service

import org.springframework.stereotype.Service

@Service
class OffenderSynchronizerService(val communityService: CommunityService, val offenderRepository: OffenderRepository, val indexStatusService: IndexStatusService) {
  fun synchroniseOffender(crn: String) : String {
    val offender = communityService.getOffender(crn)
    offenderRepository.save(offender, indexStatusService.getOrCreateCurrentIndexStatus())
    return offender.body
  }
}