package uk.gov.justice.digital.hmpps.indexer.resource

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/index")
class IndexResource {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @PostMapping("/rebuild")
  fun rebuildCurrentIndex() = log.info("Request received to rebuild index")

}