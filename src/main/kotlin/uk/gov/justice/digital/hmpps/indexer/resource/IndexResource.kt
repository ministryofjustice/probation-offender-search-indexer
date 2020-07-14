package uk.gov.justice.digital.hmpps.indexer.resource

import arrow.core.Either
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.service.BuildIndexError
import uk.gov.justice.digital.hmpps.indexer.service.IndexService

@RestController
@RequestMapping("/probation-index")
class IndexResource(private val indexService: IndexService) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/build-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun buildIndex(): ResponseEntity<IndexStatus> =
    when(val result = indexService.buildIndex()) {
      is Either.Right -> ResponseEntity.ok().body(result.b)
      is Either.Left -> when (result.a) {
        is BuildIndexError.BuildAlreadyInProgress -> ResponseEntity.status(409).body(result.a.indexStatus)
      }
    }

  @PutMapping("/mark-complete")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun markComplete() = indexService.markIndexingComplete()

  @PutMapping("/cancel-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun cancelIndex() = indexService.cancelIndexing()

  @PutMapping("/index/offender/{crn}")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun indexOffender(@PathVariable("crn") crn: String) = indexService.indexOffender(crn)

}