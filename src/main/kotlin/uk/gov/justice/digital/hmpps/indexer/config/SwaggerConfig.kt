package uk.gov.justice.digital.hmpps.indexer.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.ApiInfo
import springfox.documentation.service.Contact
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.Date
import java.util.Optional

@Configuration
@EnableSwagger2
class SwaggerConfig {

  @Autowired(required = false)
  private val buildProperties: BuildProperties? = null

  private val version: String
    get() = if (buildProperties == null) "version not available" else buildProperties.version

  @Bean
  fun api(): Docket {
    val apiInfo = ApiInfo("Probation Offender Search", "API for indexing of offenders in Elasticsearch",
        version, "", Contact("HMPPS Digital Studio", "", "feedback@digital.justice.gov.uk"),
        "Open Government Licence v3.0", "https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/", emptyList())
    val docket = Docket(DocumentationType.SWAGGER_2)
        .useDefaultResponseMessages(false)
        .apiInfo(apiInfo)
        .select()
        .apis(RequestHandlerSelectors.any())
        .paths(PathSelectors.any())
        .build()
    docket.genericModelSubstitutes(Optional::class.java)
    docket.directModelSubstitute(ZonedDateTime::class.java, Date::class.java)
    docket.directModelSubstitute(LocalDateTime::class.java, Date::class.java)
    return docket
  }

}