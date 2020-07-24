package uk.gov.justice.digital.hmpps.indexer.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase

@ExtendWith(SpringExtension::class)
class InfoTest : IntegrationTestBase() {

  @BeforeEach
  fun init() {
    deleteOffenderIndexes()
    createOffenderIndexes()
    initialiseIndexStatus()
    buildInitialIndex()
  }

  @Test
  fun `Info page reports relevant details`() {
    webTestClient.get()
        .uri("/info")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("index-status.id").doesNotExist()
        .jsonPath("index-status.currentIndex").isEqualTo("GREEN")
        .jsonPath("index-status.currentIndexStartBuildTime").value<String> { assertThat(it).isNotNull() }
        .jsonPath("index-status.currentIndexEndBuildTime").value<String> { assertThat(it).isNotNull() }
        .jsonPath("index-status.otherIndex").isEqualTo("BLUE")
        .jsonPath("index-status.otherIndexStartBuildTime").value<String> { assertThat(it).isNull() }
        .jsonPath("index-status.otherIndexEndBuildTime").value<String> { assertThat(it).isNull() }
        .jsonPath("index-size.GREEN").isEqualTo(0)
        .jsonPath("index-size.BLUE").isEqualTo(0)
  }

}
