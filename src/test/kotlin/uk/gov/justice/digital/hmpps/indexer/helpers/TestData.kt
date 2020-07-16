package uk.gov.justice.digital.hmpps.indexer.helpers

import uk.gov.justice.digital.hmpps.indexer.model.IndexState
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex
import java.time.LocalDateTime

internal fun indexStatus(currentIndex: SyncIndex, state: IndexState) =
    IndexStatus(
        currentIndex = currentIndex,
        startIndexTime = if (state == IndexState.NEW) null else LocalDateTime.now().minusHours(1),
        endIndexTime = if (state == IndexState.BUILDING) null else LocalDateTime.now().minusMinutes(1),
        state = state
    )
