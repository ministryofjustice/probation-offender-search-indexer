package uk.gov.justice.digital.hmpps.indexer.config

import com.microsoft.applicationinsights.extensibility.ContextInitializer
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
class VersionOutputter(buildProperties: BuildProperties) {
  private val version = buildProperties.version

  @EventListener(ApplicationReadyEvent::class)
  fun logVersionOnStartup() {
    log.info("Version {} started", version)
  }

  @Bean
  fun versionContextInitializer() = ContextInitializer {
    it.component.setVersion(version)
    it.cloud.role = "probation-offender-search-indexer" // Workaround bug where cloud_RoleName is not included in App Insights logs until the app has started
  }

  companion object {
    private val log = LoggerFactory.getLogger(VersionOutputter::class.java)
  }
}
