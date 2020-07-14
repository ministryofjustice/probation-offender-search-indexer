package uk.gov.justice.digital.hmpps.indexer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.service.IndexStatusService


class IndexStatusIntegrationTest : IntegrationTest() {

  companion object {
    var indexesCreated = false
  }

  @Autowired
  private lateinit var indexStatusService: IndexStatusService

  @Autowired
  private lateinit var indexStatusRepository: IndexStatusRepository

  @BeforeEach
  fun `initialise and clear database`() {
    if (indexesCreated.not()) {
      setupIndexes()
      indexesCreated = true
    }
    indexStatusRepository.deleteAll()
  }

  @Test
  fun `Should save new index status to repository`() {
    indexStatusService.getOrCreateCurrentIndexStatus()

    val actual = getActualIndexStatus()
    assertThat(actual.state).isEqualTo(IndexState.NEW)
  }

  @Test
  fun `Should save index status to repository`() {
    indexStatusService.getOrCreateCurrentIndexStatus()

    indexStatusService.markBuildInProgress()

    val actual = getActualIndexStatus()
    assertThat(actual.state).isEqualTo(IndexState.BUILDING)
    assertThat(actual.startIndexTime).isNotNull()
  }

  @Test
  fun `Should mark build index complete`() {
    indexStatusService.getOrCreateCurrentIndexStatus()
    indexStatusService.markBuildInProgress()

    indexStatusService.markBuildComplete()

    val actual = getActualIndexStatus()
    assertThat(actual.state).isEqualTo(IndexState.COMPLETED)
    assertThat(actual.endIndexTime).isNotNull()
  }

  @Test
  fun `Should mark build index cancelled`() {
    indexStatusService.getOrCreateCurrentIndexStatus()
    indexStatusService.markBuildInProgress()

    indexStatusService.markBuildCancelled()

    val actual = getActualIndexStatus()
    assertThat(actual.state).isEqualTo(IndexState.CANCELLED)
    assertThat(actual.endIndexTime).isNull()
  }

  @Test
  fun `Should not mark cancelled if not building`() {
    indexStatusService.getOrCreateCurrentIndexStatus()
    indexStatusService.markBuildInProgress()
    indexStatusService.markBuildComplete()

    indexStatusService.markBuildCancelled()

    val actual = getActualIndexStatus()
    assertThat(actual.state).isEqualTo(IndexState.COMPLETED)
  }

  @Test
  fun `Should not mark completed if not building`() {
    indexStatusService.getOrCreateCurrentIndexStatus()
    indexStatusService.markBuildInProgress()
    indexStatusService.markBuildCancelled()

    indexStatusService.markBuildComplete()

    val actual = getActualIndexStatus()
    assertThat(actual.state).isEqualTo(IndexState.CANCELLED)
  }

  private fun getActualIndexStatus() = indexStatusRepository.findById(INDEX_STATUS_ID).orElseGet { fail("Should find index status in repository") }
}
