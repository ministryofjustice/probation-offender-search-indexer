package uk.gov.justice.digital.hmpps.indexer.resource

import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.indexer.service.IndexService

@RestController
@RequestMapping("/probation-index")
class IndexResource(private val indexService: IndexService) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/build-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun buildIndex() = indexService.buildIndex()

  @PutMapping("/mark-complete")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun markComplete() = indexService.markIndexingComplete()

}