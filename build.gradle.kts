plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "3.1.7"
  kotlin("plugin.spring") version "1.4.32"
  id("org.unbroken-dome.test-sets") version "3.0.1"
}

dependencyCheck {
  suppressionFiles.add("elasticsearch-suppressions.xml")
}

testSets {
  "testSmoke"()
}

dependencies {

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.data:spring-data-elasticsearch")

  implementation("org.springframework:spring-jms")
  implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
  implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.11.1001")

  implementation("com.google.code.gson:gson:2.8.6")
  // arrow-core not updated to 0.13.0 to avoid possible bug when calling Either.shouldBeLeft
  implementation("io.arrow-kt:arrow-core:0.12.0")

  implementation("org.springdoc:springdoc-openapi-ui:1.5.7")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.5.7")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.5.7")

  implementation("org.awaitility:awaitility-kotlin:4.0.3")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.testcontainers:localstack:1.15.3")
  testImplementation("org.testcontainers:elasticsearch:1.15.3")
  testImplementation("io.kotest:kotest-assertions-arrow-jvm:4.4.3")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.25.0")
  testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.0.25")
  testImplementation("org.mockito:mockito-inline:3.9.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.4.3")
}

tasks {
  test {
    maxHeapSize = "256m"
  }
}
