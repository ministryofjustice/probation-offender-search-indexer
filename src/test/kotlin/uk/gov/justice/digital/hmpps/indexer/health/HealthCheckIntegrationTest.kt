package uk.gov.justice.digital.hmpps.indexer.health

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doCallRealMethod
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.indexer.integration.IntegrationTest


@ExtendWith(SpringExtension::class)
class HealthCheckIntegrationTest : IntegrationTest() {
  @Autowired
  private lateinit var eventQueueHealth: QueueHealth

  @Autowired
  private lateinit var indexQueueHealth: QueueHealth

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var webTestClient: WebTestClient

  @Autowired
  @Value("\${event.sqs.queue.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var eventQueueName: String

  @Autowired
  @Value("\${index.sqs.queue.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var indexQueueName: String

  @Autowired
  @Value("\${event.sqs.dlq.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var eventDlqName: String

  @Autowired
  @Value("\${index.sqs.dlq.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var indexDlqName: String

  @AfterEach
  fun tearDown() {
    ReflectionTestUtils.setField(eventQueueHealth, "queueName", eventQueueName)
    ReflectionTestUtils.setField(eventQueueHealth, "dlqName", eventDlqName)
    ReflectionTestUtils.setField(indexQueueHealth, "queueName", indexQueueName)
    ReflectionTestUtils.setField(indexQueueHealth, "dlqName", indexDlqName)
  }

  @Test
  fun `Health page reports ok`() {
    stubPingWithResponse(200)

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("OK")
        .jsonPath("components.communityApiHealth.details.HttpStatus").isEqualTo("OK")
        .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health ping page is accessible`() {
    stubPingWithResponse(200)

    webTestClient.get()
        .uri("/health/ping")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health page reports down`() {
    stubPingWithResponse(404)

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .is5xxServerError
        .expectBody()
        .jsonPath("status").isEqualTo("DOWN")
        .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
        .jsonPath("components.communityApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
  }

  @Test
  fun `Health page reports a teapot`() {
    stubPingWithResponse(418)

    webTestClient.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .is5xxServerError
        .expectBody()
        .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
        .jsonPath("components.communityApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
        .jsonPath("status").isEqualTo("DOWN")
  }

  @Nested
  inner class EventQueue {
    @Test
    fun `Queue does not exist reports down`() {
      ReflectionTestUtils.setField(eventQueueHealth, "queueName", "missing_queue")
      stubPingWithResponse(200)

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .is5xxServerError
          .expectBody()
          .jsonPath("components.eventQueueHealth.status").isEqualTo("DOWN")
          .jsonPath("status").isEqualTo("DOWN")

    }

    @Test
    fun `Queue health ok and dlq health ok, reports everything up`() {
      stubPingWithResponse(200)

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("components.eventQueueHealth.status").isEqualTo("UP")
          .jsonPath("components.eventQueueHealth.status").isEqualTo(DlqStatus.UP.description)
          .jsonPath("status").isEqualTo("UP")

    }

    @Test
    fun `Dlq health reports interesting attributes`() {
      stubPingWithResponse(200)

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("components.eventQueueHealth.details.${QueueAttributes.MESSAGES_ON_DLQ.healthName}").isEqualTo(0)

    }

    @Test
    fun `Dlq not found reports dlq down`() {
      stubPingWithResponse(200)
      ReflectionTestUtils.setField(eventQueueHealth, "dlqName", "missing_queue")

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .is5xxServerError
          .expectBody()
          .jsonPath("components.eventQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)

    }
  }

  @Nested
  inner class IndexQueue {
    @Test
    fun `Queue does not exist reports down`() {
      ReflectionTestUtils.setField(indexQueueHealth, "queueName", "missing_queue")
      stubPingWithResponse(200)

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .is5xxServerError
          .expectBody()
          .jsonPath("components.indexQueueHealth.status").isEqualTo("DOWN")
          .jsonPath("status").isEqualTo("DOWN")

    }

    @Test
    fun `Queue health ok and dlq health ok, reports everything up`() {
      stubPingWithResponse(200)

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("components.indexQueueHealth.status").isEqualTo("UP")
          .jsonPath("components.indexQueueHealth.status").isEqualTo(DlqStatus.UP.description)
          .jsonPath("status").isEqualTo("UP")

    }

    @Test
    fun `Dlq health reports interesting attributes`() {
      stubPingWithResponse(200)

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("components.indexQueueHealth.details.${QueueAttributes.MESSAGES_ON_DLQ.healthName}").isEqualTo(0)

    }

    @Test
    fun `Dlq not found reports dlq down`() {
      stubPingWithResponse(200)
      ReflectionTestUtils.setField(indexQueueHealth, "dlqName", "missing_queue")

      webTestClient.get()
          .uri("/health")
          .exchange()
          .expectStatus()
          .is5xxServerError
          .expectBody()
          .jsonPath("components.indexQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)

    }
  }

  private fun stubPingWithResponse(status: Int) {
    oauthMockServer.stubFor(get("/auth/health/ping").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(if (status == 200) "pong" else "some error")
        .withStatus(status)))

    communityMockServer.stubFor(get("/health/ping").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(if (status == 200) "pong" else "some error")
        .withStatus(status)))
  }

}
