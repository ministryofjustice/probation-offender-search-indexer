plugins {
	id("uk.gov.justice.hmpps.gradle-spring-boot") version "0.4.2"
	kotlin("plugin.spring") version "1.3.72"
}

configurations {
	testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
	implementation("org.springframework:spring-jms")
	implementation("com.amazonaws:amazon-sqs-java-messaging-lib:1.0.8")

	implementation( "com.google.code.gson:gson:2.8.6")


	testImplementation("com.github.tomakehurst:wiremock-standalone:2.26.3")
	testImplementation("org.testcontainers:localstack:1.14.3")
	testImplementation("org.awaitility:awaitility-kotlin:4.0.3")
}
