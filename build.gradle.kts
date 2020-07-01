plugins {
	id("uk.gov.justice.hmpps.gradle-spring-boot") version "0.4.2"
	kotlin("plugin.spring") version "1.3.72"
}

configurations {
	testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
}
