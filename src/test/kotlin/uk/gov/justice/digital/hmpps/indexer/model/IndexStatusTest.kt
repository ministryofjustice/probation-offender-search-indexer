package uk.gov.justice.digital.hmpps.indexer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndexStatusTest {

  @Test
  fun `toggle index`() {
    val indexStatus = IndexStatus(currentIndex = SyncIndex.GREEN, inProgress = true, startIndexTime = null, endIndexTime = null)

    indexStatus.toggleIndex()
    assertThat(indexStatus.currentIndex).isEqualTo(SyncIndex.BLUE)

    indexStatus.toggleIndex()
    assertThat(indexStatus.currentIndex).isEqualTo(SyncIndex.GREEN)
  }
}