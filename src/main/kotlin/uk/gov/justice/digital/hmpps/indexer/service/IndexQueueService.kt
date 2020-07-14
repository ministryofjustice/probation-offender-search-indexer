package uk.gov.justice.digital.hmpps.indexer.service

import org.springframework.stereotype.Service

@Service
interface IndexQueueService {
  fun sendIndexRequestMessage()
}
