package uk.gov.justice.digital.hmpps.indexer.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.elasticsearch.ElasticsearchContainer

@Configuration
@ConditionalOnProperty(name = ["aws.provider"], havingValue = "testcontainers-localstack")
class TestContainersLocalStackConfig {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private const val ELASTICSEARCH_DEFAULT_PORT = 9200
  }

  @Bean
  fun localStackContainer(applicationContext: ConfigurableApplicationContext): LocalStackContainer {
    startElasticSearchContainer(applicationContext)
    log.info("Starting localstack...")
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    val localStackContainer: LocalStackContainer = LocalStackContainer("0.11.2")
      .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      .withClasspathResourceMapping("/localstack/setup-sns.sh", "/docker-entrypoint-initaws.d/setup-sns.sh", BindMode.READ_WRITE)
      .withEnv("HOSTNAME_EXTERNAL", "localhost")
      .withEnv("DEFAULT_REGION", "eu-west-2")
      .waitingFor(
        Wait.forLogMessage(".*All Ready.*", 1)
      )

    log.info("Started localstack.")

    localStackContainer.start()
    localStackContainer.followOutput(logConsumer)
    return localStackContainer
  }

  private fun startElasticSearchContainer(applicationContext: ConfigurableApplicationContext) {
    log.info("Starting elasticsearch...")
    val elasticSearchPort = ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.6.1")
      .withEnv("HOSTNAME_EXTERNAL", "localhost")
      .withClasspathResourceMapping("/localstack/setup-es.sh", "/docker-entrypoint-initaws.d/setup-es.sh", BindMode.READ_WRITE)
      .withExposedPorts(ELASTICSEARCH_DEFAULT_PORT)
      .let {
        it.start()
        it.getMappedPort(ELASTICSEARCH_DEFAULT_PORT)
      }
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, "elasticsearch.port=$elasticSearchPort")
    log.info("Started elasticsearch on port {}", elasticSearchPort)
  }
}
