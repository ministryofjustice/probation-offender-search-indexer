package uk.gov.justice.digital.hmpps.indexer.integration.resource

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTestBase

class SwaggerValidatorTest : IntegrationTestBase() {
  @Test
  fun `open api docs are available`() {
    webTestClient.get()
        .uri("/swagger-ui/index.html")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk
  }
}
