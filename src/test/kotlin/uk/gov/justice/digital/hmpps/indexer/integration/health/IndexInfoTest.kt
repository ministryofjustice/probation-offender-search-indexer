package uk.gov.justice.digital.hmpps.indexer.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.indexer.integration.wiremock.CommunityApiExtension
import uk.gov.justice.digital.hmpps.indexer.model.SyncIndex

@ExtendWith(SpringExtension::class)
class IndexInfoTest : IntegrationTestBase() {

  @Nested
  inner class WhenNoIndexesArePresent {
    @BeforeEach
    fun init() {
      deleteOffenderIndexes()
      deinitialiseIndexStatus()
    }

    @Test
    fun `Info page reports relevant details`() {
      webTestClient.get()
          .uri("/info")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("index-status").isEqualTo("No status exists yet")
          .jsonPath("index-status.currentIndex").doesNotExist()
          .jsonPath("index-status.otherIndex").doesNotExist()
          .jsonPath("index-size.GREEN").isEqualTo(-1)
          .jsonPath("index-size.BLUE").isEqualTo(-1)
          .jsonPath("offender-alias").isEqualTo("")
          .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }

  @Nested
  inner class WhenIndexesArePresent {

    @BeforeEach
    fun init() {
      deleteOffenderIndexes()
      createOffenderIndexes()
      initialiseIndexStatus()
      CommunityApiExtension.communityApi.stubAllOffenderGets(10 )
      buildAndSwitchIndex(SyncIndex.GREEN, 0)
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
          .jsonPath("offender-alias").isEqualTo("probation-search-green")
          .jsonPath("index-queue-backlog").isEqualTo("0")
    }
  }

}
