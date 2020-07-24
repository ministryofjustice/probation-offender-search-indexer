package uk.gov.justice.digital.hmpps.indexer.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.integration.repository.OffenderRepository

@Service
class OffenderSynchroniserService(val communityService: CommunityService, val offenderRepository: OffenderRepository, @Value("\${index.page.size:1000}") private val pageSize: Long) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun synchroniseOffender(crn: String, vararg indexes: SyncIndex): String {
    val offender = communityService.getOffender(crn)
    indexes.map { offenderRepository.save(offender, it) }
    return offender.json
  }

  fun checkExistsAndReset(index: SyncIndex) {
    if (offenderRepository.doesIndexExist(index)) {
      offenderRepository.deleteIndex(index)
    }
    offenderRepository.createIndex(index)
  }

  fun switchAliasIndex(index: SyncIndex) {
    offenderRepository.switchAliasIndex(index)
  }

  fun splitAllOffendersIntoChunks(): List<OffenderPage> {
    val totalNumberOfOffenders = communityService.getCountAllOffenders().totalElements
    log.info("Splitting $totalNumberOfOffenders in to pages each of size $pageSize")
    return (1..totalNumberOfOffenders step pageSize).asSequence().toList()
        .map { OffenderPage(it / pageSize, pageSize) }
  }

  fun getAllOffenderIdentifiersInPage(offenderPage: OffenderPage): List<OffenderIdentifier> {
    return communityService.getPageOfOffenders(offenderPage.page, offenderPage.pageSize)
        .content.map { OffenderIdentifier(it.crn) }
  }

}

data class OffenderPage(val page: Long, val pageSize: Long)