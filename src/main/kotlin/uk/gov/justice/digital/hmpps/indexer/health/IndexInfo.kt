package uk.gov.justice.digital.hmpps.indexer.health


import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.IndexStatusService


@Component
class IndexInfo(
    private val indexStatusService : IndexStatusService,
    private val indexService: IndexService
) : InfoContributor {

  override fun contribute(builder : Info.Builder) {
    val indexStatus = indexStatusService.getIndexStatus()
    builder.withDetail("index-status", indexStatus)
    builder.withDetail("index-size", mapOf(
      GREEN to indexService.getIndexCount(GREEN),
      BLUE to indexService.getIndexCount(BLUE)
    ))
  }

}
