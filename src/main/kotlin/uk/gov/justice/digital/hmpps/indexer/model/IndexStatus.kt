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
  val currentIndexStartBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  val currentIndexEndBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Text)
  val currentIndexState: IndexState = IndexState.NEW,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  val otherIndexStartBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  val otherIndexEndBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Text)
  val otherIndexState: IndexState = IndexState.NEW

) {

  val otherIndex
    get() = currentIndex.otherIndex()

  fun inProgress(): Boolean {
    return this.otherIndexState == IndexState.BUILDING
  }

  fun toBuildInProgress(): IndexStatus {
    return this.copy(
        otherIndexStartBuildTime = LocalDateTime.now(),
        otherIndexEndBuildTime = null,
        otherIndexState = IndexState.BUILDING
    )
  }

  fun toBuildComplete(): IndexStatus {
    return this.copy(
        otherIndexEndBuildTime = LocalDateTime.now(),
        otherIndexState = IndexState.COMPLETED
    )
  }

  fun toSwitchIndex(): IndexStatus {
    return this.copy(
        currentIndex = otherIndex,
        currentIndexStartBuildTime = otherIndexStartBuildTime,
        currentIndexEndBuildTime = otherIndexEndBuildTime,
        currentIndexState = otherIndexState,
        otherIndexStartBuildTime = null,
        otherIndexEndBuildTime = null,
        otherIndexState = IndexState.NEW
    )
  }

  fun toBuildCancelled(): IndexStatus {
    return this.copy(otherIndexEndBuildTime = LocalDateTime.now(), otherIndexState = IndexState.CANCELLED)
  }

  companion object {
    fun newIndex() = IndexStatus(currentIndex = SyncIndex.GREEN)
  }
}