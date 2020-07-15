package uk.gov.justice.digital.hmpps.indexer.service

import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.core.CountResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class SearchClient(
    @param:Qualifier("elasticSearchClient") private val elasticSearchClient: RestHighLevelClient
) {
  fun count(countRequest: CountRequest): CountResponse = elasticSearchClient.count(countRequest, RequestOptions.DEFAULT)
  fun lowLevelClient() : RestClient = elasticSearchClient.lowLevelClient
}