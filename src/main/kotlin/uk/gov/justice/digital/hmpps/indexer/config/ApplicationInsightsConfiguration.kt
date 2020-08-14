package uk.gov.justice.digital.hmpps.indexer.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.*
import org.springframework.core.type.AnnotatedTypeMetadata

enum class TelemetryEvents {
    BUILDING_INDEX, CANCELLED_BUILDING_INDEX, COMPLETED_BUILDING_INDEX, PURGED_INDEX_QUEUE,
    PURGED_INDEX_DLQ, TRANSFERRED_INDEX_DLQ, POPULATE_OFFENDER_PAGES, OFFENDER_UPDATED,
    BUILD_INDEX_MSG, BUILD_PAGE_MSG, BUILD_PAGE_BOUNDARY
}

/**
 * Application insights now controlled by the spring-boot-starter dependency.  However when the key is not specified
 * we don't get a telemetry bean and application won't start.  Therefore need this backup configuration.
 */
@Configuration
class ApplicationInsightsConfiguration {
    @Bean
    @Conditional(AppInsightKeyAbsentCondition::class)
    fun telemetryClient(): TelemetryClient = TelemetryClient()

    class AppInsightKeyAbsentCondition : Condition {
        override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
            context.environment.getProperty("application.insights.ikey").isNullOrBlank()
    }
}
