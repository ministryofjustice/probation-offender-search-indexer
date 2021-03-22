package uk.gov.justice.digital.hmpps.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("index-build")
data class IndexBuildProperties(val completeThreshold: Long)
