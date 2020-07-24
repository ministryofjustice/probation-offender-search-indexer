package uk.gov.justice.digital.hmpps.indexer.integration.repository

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus

interface IndexStatusRepository : ElasticsearchRepository<IndexStatus, String>