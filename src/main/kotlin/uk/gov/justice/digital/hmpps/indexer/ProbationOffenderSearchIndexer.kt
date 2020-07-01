package uk.gov.justice.digital.hmpps.indexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProbationOffenderSearchIndexer

fun main(args: Array<String>) {
    runApplication<ProbationOffenderSearchIndexer>(*args)
}