package uk.gov.justice.digital.hmpps.indexer.helpers

import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import java.time.LocalDateTime

internal fun indexStatus(otherIndex: SyncIndex, otherIndexState: IndexState) =
    IndexStatus(
        currentIndex = otherIndex.otherIndex(),
        currentIndexStartBuildTime = null,
        currentIndexEndBuildTime = null,
        currentIndexState = IndexState.ABSENT,
        otherIndexStartBuildTime = if (otherIndexState == IndexState.ABSENT) null else LocalDateTime.now().minusHours(1),
        otherIndexEndBuildTime = if (listOf(IndexState.ABSENT, IndexState.BUILDING).contains(otherIndexState)) null else LocalDateTime.now().minusMinutes(1),
        otherIndexState = otherIndexState
    )
