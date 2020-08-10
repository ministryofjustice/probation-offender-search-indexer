package uk.gov.justice.digital.hmpps.indexer.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import uk.gov.justice.digital.hmpps.indexer.repository.OffenderRepository

@Service
class OffenderSynchroniserService(
    val communityService: CommunityService,
    val offenderRepository: OffenderRepository,
    val telemetryClient: TelemetryClient,
    @Value("\${index.page.size:10000}") private val pageSize: Long
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  internal fun synchroniseOffender(crn: String, vararg indexes: SyncIndex): Either<OffenderError, String> =
      communityService.getOffender(crn)
          .flatMap {
            indexes.map { index -> offenderRepository.save(it, index) }
            it.json.right()
                .also { telemetryClient.trackEvent(TelemetryEvents.OFFENDER_UPDATED.name, mapOf("crn" to crn), null) }
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
        .also { telemetryClient.trackEvent(TelemetryEvents.POPULATE_OFFENDER_PAGES.name, mapOf("totalNumberOfOffenders" to totalNumberOfOffenders.toString(), "pageSize" to pageSize.toString()), null) }
  }

  fun getAllOffenderIdentifiersInPage(offenderPage: OffenderPage): List<OffenderIdentifier> {
    return communityService.getPageOfOffenders(offenderPage.page, offenderPage.pageSize)
        .content.map { OffenderIdentifier(it.crn) }
  }

}

data class OffenderPage(val page: Long, val pageSize: Long)