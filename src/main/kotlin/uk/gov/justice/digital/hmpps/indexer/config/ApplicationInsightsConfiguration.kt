package uk.gov.justice.digital.hmpps.indexer.config

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

enum class TelemetryEvents {
  BUILDING_INDEX, CANCELLED_BUILDING_INDEX, COMPLETED_BUILDING_INDEX, SWITCH_INDEX, PURGED_INDEX_QUEUE,
  PURGED_INDEX_DLQ, PURGED_EVENT_DLQ, TRANSFERRED_INDEX_DLQ, TRANSFERRED_EVENT_DLQ, POPULATE_OFFENDER_PAGES,
  OFFENDER_UPDATED, BUILD_INDEX_MSG, BUILD_PAGE_MSG, BUILD_PAGE_BOUNDARY
}

/**
 * Application insights now controlled by the spring-boot-starter dependency.  However when the key is not specified
 * we don't get a telemetry bean and application won't start.  Therefore need this backup configuration.
 */
@Configuration
class ApplicationInsightsConfiguration {
  @Bean
  @ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isBlank('\${applicationinsights.connection.string:}')")
  fun telemetryClient(): TelemetryClient {
    log.warn("Application insights configuration missing, returning dummy bean instead")

    return TelemetryClient()
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
