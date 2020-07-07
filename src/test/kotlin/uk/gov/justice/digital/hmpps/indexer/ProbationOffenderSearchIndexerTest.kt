package uk.gov.justice.digital.hmpps.indexer

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles(profiles = ["test"])
class ProbationOffenderSearchIndexerTest {
  @Test
  fun `The application starts`() {}
}

