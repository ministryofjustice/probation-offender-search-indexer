package uk.gov.justice.digital.hmpps.indexer.resource

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.indexer.service.SearchService

@RestController
@RequestMapping("/probation-search")
class SearchResource(private val searchService: SearchService) {

  @PostMapping("/match")
  fun match() = searchService.match()
}