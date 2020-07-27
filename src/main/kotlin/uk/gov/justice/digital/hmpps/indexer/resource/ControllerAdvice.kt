package uk.gov.justice.digital.hmpps.indexer.resource

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.lang.Exception

@RestControllerAdvice
class ControllerAdvice {

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<String> {
    throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.message, e)
  }

}