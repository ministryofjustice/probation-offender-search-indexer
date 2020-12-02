package uk.gov.justice.digital.hmpps.indexer.config

import org.elasticsearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.RestClients
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration

@Configuration
class ElasticSearchConfiguration : AbstractElasticsearchConfiguration() {

  @Value("\${elasticsearch.port}")
  private val port = 0

  @Value("\${elasticsearch.host}")
  private val host: String? = null

  @Bean("elasticSearchClient")
  override fun elasticsearchClient(): RestHighLevelClient {
    return RestClients.create(ClientConfiguration.builder().connectedTo("$host:$port").build()).rest()
  }
}
