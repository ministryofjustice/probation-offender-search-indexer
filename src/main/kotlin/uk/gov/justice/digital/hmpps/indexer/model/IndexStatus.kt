package uk.gov.justice.digital.hmpps.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

internal const val INDEX_STATUS_ID = "STATUS"

enum class IndexState { NEW, BUILDING, CANCELLED, COMPLETED }

@Document(indexName = "offender-index-status")
data class IndexStatus(
  @Id
  @Field(type = FieldType.Keyword)
  val id: String = INDEX_STATUS_ID,

  @Field(type = FieldType.Keyword)
  val currentIndex: SyncIndex,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  val startIndexTime: LocalDateTime?,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  val endIndexTime: LocalDateTime?,

  @Field(type = FieldType.Text)
  val state: IndexState

) {

  fun inProgress(): Boolean {
    return this.state == IndexState.BUILDING
  }

  fun toBuildInProgress(): IndexStatus {
    return this.copy(startIndexTime = LocalDateTime.now(), endIndexTime = null, state = IndexState.BUILDING)
  }

  fun toBuildComplete(): IndexStatus {
    return this.copy(currentIndex = this.currentIndex.otherIndex(), endIndexTime = LocalDateTime.now(), state = IndexState.COMPLETED)
  }

  fun toBuildCancelled(): IndexStatus {
    return this.copy(currentIndex = this.currentIndex.otherIndex(), state = IndexState.CANCELLED)
  }

  companion object {
    fun newIndex() = IndexStatus(INDEX_STATUS_ID, SyncIndex.GREEN, null, null, IndexState.NEW)
  }
}