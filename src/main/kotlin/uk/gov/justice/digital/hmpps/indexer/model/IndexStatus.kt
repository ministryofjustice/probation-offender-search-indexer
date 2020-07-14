package uk.gov.justice.digital.hmpps.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

internal const val INDEX_STATUS_ID = "STATUS"

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

  @Field(type = FieldType.Boolean)
  val inProgress: Boolean

) {

}