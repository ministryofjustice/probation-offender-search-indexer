package uk.gov.justice.digital.hmpps.indexer.model

enum class SyncIndex(val indexName : String) {

  GREEN("probation-search-green"), BLUE("probation-search-blue");

  fun otherIndex() : SyncIndex {
    return if (this == GREEN) BLUE else GREEN
  }
}
