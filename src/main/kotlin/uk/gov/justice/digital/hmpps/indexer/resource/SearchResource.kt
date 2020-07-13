package uk.gov.justice.digital.hmpps.indexer.resource

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.indexer.service.SearchService

@RestController
@RequestMapping("/probation-search")
class SearchResource(private val searchService: SearchService) {

  @PutMapping("/match")
  @PreAuthorize("hasRole('GLOBAL_SEARCH')")
  fun match() = searchService.match()
}