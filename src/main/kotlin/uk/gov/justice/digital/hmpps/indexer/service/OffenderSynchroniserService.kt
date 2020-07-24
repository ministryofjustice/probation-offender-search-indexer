package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository

@Service
class OffenderSynchroniserService(val communityService: CommunityService, val offenderRepository: OffenderRepository, @Value("\${index.page.size:1000}") private val pageSize: Long) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun synchroniseOffender(crn: String, vararg indexes: SyncIndex): Either<SynchroniseOffenderError, String> =
    communityService.getOffender(crn)
        .map {
          indexes.map { index -> offenderRepository.save(it, index) }
          it.json.right()
        }
        .getOrHandle {
          when(it) {
            is GetOffenderError.OffenderNotFound -> SynchroniseOffenderError.OffenderNotFound(it.message).left()
          }
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

sealed class SynchroniseOffenderError(val message: String) {
  data class OffenderNotFound(val crn: String) : SynchroniseOffenderError("The offender $crn could not be found")
}
