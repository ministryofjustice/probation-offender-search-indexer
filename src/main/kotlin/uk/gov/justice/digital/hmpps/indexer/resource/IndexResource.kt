package uk.gov.justice.digital.hmpps.indexer.resource

import arrow.core.getOrHandle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.service.BuildIndexError
import uk.gov.justice.digital.hmpps.indexer.service.CancelBuildIndexError
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.MarkBuildCompleteError

@RestController
@RequestMapping("/probation-index")
class IndexResource(private val indexService: IndexService) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/build-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun buildIndex(): IndexStatus =
      indexService.prepareIndexForRebuild()
          .getOrHandle { error ->
            when (error) {
              is BuildIndexError.BuildAlreadyInProgress -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message)
            }
          }

  @PutMapping("/mark-complete")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun markComplete() =
      indexService.markIndexingComplete()
          .getOrHandle { error ->
            when (error) {
              is MarkBuildCompleteError.BuildNotInProgress -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message)
            }
          }

  @PutMapping("/cancel-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun cancelIndex() =
      indexService.cancelIndexing()
          .getOrHandle { error ->
            when (error) {
              is CancelBuildIndexError.BuildNotInProgress -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message)
            }
          }

  @PutMapping("/index/offender/{crn}")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun indexOffender(@PathVariable("crn") crn: String) = indexService.indexOffender(crn)

}