package uk.gov.justice.digital.hmpps.indexer.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.indexer.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.service.IndexStatusService

@Disabled("Being fixed on another branch")
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
      indexStatusService.initialiseIndexWhenRequired()
      indexesCreated = true
    }
    indexStatusRepository.save(IndexStatus.newIndex())
  }

  @Test
  fun `Should save new index status to repository`() {
    indexStatusService.getIndexStatus()

    val actual = getActualIndexStatus()
    assertThat(actual.otherIndexState).isEqualTo(IndexState.NEW)
  }

  @Test
  fun `Should save index status to repository`() {
    indexStatusService.markBuildInProgress()

    val actual = getActualIndexStatus()
    assertThat(actual.otherIndexState).isEqualTo(IndexState.BUILDING)
    assertThat(actual.otherIndexStartBuildTime).isNotNull()
  }

  @Test
  fun `Should mark build index complete and switch to current`() {
    indexStatusService.markBuildInProgress()

    indexStatusService.markBuildCompleteAndSwitchIndex()

    val actual = getActualIndexStatus()
    assertThat(actual.currentIndexState).isEqualTo(IndexState.COMPLETED)
    assertThat(actual.currentIndexEndBuildTime).isNotNull()
  }

  @Test
  fun `Should mark build index cancelled`() {
    indexStatusService.markBuildInProgress()

    indexStatusService.markBuildCancelled()

    val actual = getActualIndexStatus()
    assertThat(actual.otherIndexState).isEqualTo(IndexState.CANCELLED)
    assertThat(actual.otherIndexEndBuildTime).isNotNull()
  }

  @Test
  fun `Should not mark cancelled if not building`() {
    indexStatusService.markBuildInProgress()
    indexStatusService.markBuildCompleteAndSwitchIndex()

    indexStatusService.markBuildCancelled()

    val actual = getActualIndexStatus()
    assertThat(actual.otherIndexState).isNotEqualTo(IndexState.CANCELLED)
  }

  @Test
  fun `Should not mark completed if not building`() {
    indexStatusService.markBuildInProgress()
    indexStatusService.markBuildCancelled()

    indexStatusService.markBuildCompleteAndSwitchIndex()

    val actual = getActualIndexStatus()
    assertThat(actual.otherIndexState).isNotEqualTo(IndexState.COMPLETED)
  }

  private fun getActualIndexStatus() = indexStatusRepository.findById(INDEX_STATUS_ID).orElseGet { fail("Should find index status in repository") }
}
