plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "2.1.2"
  kotlin("plugin.spring") version "1.4.21"
}

dependencyCheck {
  suppressionFiles.add("elasticsearch-suppressions.xml")
}

dependencies {

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.data:spring-data-elasticsearch")

  implementation("org.springframework:spring-jms")
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.11.934")

  implementation("com.google.code.gson:gson:2.8.6")
  implementation("io.arrow-kt:arrow-core:0.11.0")

  implementation("org.springdoc:springdoc-openapi-ui:1.5.2")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.5.2")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.2")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.testcontainers:localstack:1.15.1")
  testImplementation("org.testcontainers:elasticsearch:1.15.1")
  testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
  testImplementation("io.kotest:kotest-assertions-arrow-jvm:4.3.2")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.22.0")
  testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.0.24")
  testImplementation("org.mockito:mockito-inline:3.7.0")
}
