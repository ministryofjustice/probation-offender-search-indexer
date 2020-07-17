plugins {
	id("uk.gov.justice.hmpps.gradle-spring-boot") version "0.4.3"
	kotlin("plugin.spring") version "1.3.72"
}

configurations {
	testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {

	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.data:spring-data-elasticsearch")

	implementation("org.springframework:spring-jms")
	implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")
	implementation("com.amazonaws:aws-java-sdk-elasticsearch:1.11.809")

	implementation("com.google.code.gson:gson:2.8.6")
	implementation("io.arrow-kt:arrow-core:0.10.5")

	implementation("io.springfox:springfox-swagger2:2.9.2")
	implementation("io.springfox:springfox-swagger-ui:2.9.2")
	implementation("io.swagger:swagger-core:1.6.1")

	testImplementation("com.github.tomakehurst:wiremock-standalone:2.26.3")
	testImplementation("org.testcontainers:localstack:1.14.3")
	testImplementation("org.testcontainers:elasticsearch:1.14.3")
	testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
	testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("io.jsonwebtoken:jjwt:0.9.1")
	testImplementation("net.javacrumbs.json-unit:json-unit-assertj:0.0.15")
	testImplementation("io.swagger.parser.v3:swagger-parser-v2-converter:2.0.20")
}
