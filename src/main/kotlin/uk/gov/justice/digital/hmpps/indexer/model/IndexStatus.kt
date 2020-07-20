package uk.gov.justice.digital.hmpps.indexer.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

internal const val INDEX_STATUS_ID = "STATUS"

enum class IndexState { NEW, BUILDING, CANCELLED, COMPLETED }

@Document(indexName = "offender-index-status")
@ApiModel(description = "The status of the two indexes, the current index being actively used for searches and the other index being inactive but available for rebuilding ")
data class IndexStatus(
  @Id
  @Field(type = FieldType.Keyword)
  @JsonIgnore
  val id: String = INDEX_STATUS_ID,

  @Field(type = FieldType.Keyword)
  @ApiModelProperty(value = "The index currently active for searches", example = "GREEN")
  val currentIndex: SyncIndex,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  @ApiModelProperty(value = "The last time the current index started building", example = "2020-07-17T10:25:49.842Z")
  val currentIndexStartBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  @ApiModelProperty(value = "The last time the current index finished building", example = "2020-07-17T11:35:29.833Z")
  val currentIndexEndBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Text)
  @ApiModelProperty(value = "The status of the current index before it became active", example = "COMPLETED")
  val currentIndexState: IndexState = IndexState.NEW,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  @ApiModelProperty(value = "The time the inactive index started building", example = "2020-07-17T12:26:48.822Z")
  val otherIndexStartBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
  @ApiModelProperty(value = "The time the inactive index ended building", example = "null")
  val otherIndexEndBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Text)
  @ApiModelProperty(value = "The status of the inactive index", example = "BUILDING")
  val otherIndexState: IndexState = IndexState.NEW

) {

  val otherIndex
    @ApiModelProperty(value = "The index currently available for rebuilding", example = "BLUE")
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