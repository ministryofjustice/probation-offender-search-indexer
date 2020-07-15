package uk.gov.justice.digital.hmpps.indexer.service

import com.amazonaws.util.IOUtils
import com.google.gson.JsonParser
import org.elasticsearch.client.Request
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.core.CountResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@Service
class SearchClient(
    @param:Qualifier("elasticSearchClient") private val elasticSearchClient: RestHighLevelClient
) {
  fun count(countRequest: CountRequest): CountResponse = elasticSearchClient.count(countRequest, RequestOptions.DEFAULT)
  fun lowLevelClient() : RestClient = elasticSearchClient.lowLevelClient

  fun countIndex(syncIndex: SyncIndex): Int {
    val response = lowLevelClient().performRequest(Request("get", "/${syncIndex.indexName}/_count"))
    return JsonParser.parseString(IOUtils.toString(response.entity.content)).asJsonObject["count"].asInt
  }

}