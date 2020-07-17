package uk.gov.justice.digital.hmpps.indexer.resource

import io.swagger.v3.parser.converter.SwaggerConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTest

class SwaggerValidator : IntegrationTest() {
  @Test
  fun `validate swagger json`() {
    val result = SwaggerConverter().readLocation("http://localhost:$port/v2/api-docs", null, null)
    assertThat(result.messages).isEmpty()
  }
}
