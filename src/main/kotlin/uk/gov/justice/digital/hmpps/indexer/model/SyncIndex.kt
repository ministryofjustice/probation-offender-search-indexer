package uk.gov.justice.digital.hmpps.indexer.model

enum class SyncIndex(val indexName : String) {

  GREEN("probation-search-green"), BLUE("probation-search-blue"), NONE("new-indexes");

  fun otherIndex() : SyncIndex {
    return when (this) {
      GREEN -> BLUE
      BLUE -> GREEN
      NONE -> GREEN
    }
  }
}
