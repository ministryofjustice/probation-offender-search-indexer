package uk.gov.justice.digital.hmpps.indexer.resource

import org.slf4j.LoggerFactory
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
  fun rebuildCurrentIndex() = indexService.rebuildIndex()

}