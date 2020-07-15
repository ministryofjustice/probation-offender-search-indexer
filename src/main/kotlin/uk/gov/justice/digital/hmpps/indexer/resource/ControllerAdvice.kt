package uk.gov.justice.digital.hmpps.indexer.resource

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus

class ForbiddenException(val indexStatus: IndexStatus): RuntimeException()

@RestControllerAdvice
class ControllerAdvice {

  @ExceptionHandler(ForbiddenException::class)
  fun handleException(forbiddenException: ForbiddenException): ResponseEntity<IndexStatus> =
      ResponseEntity.status(409).body(forbiddenException.indexStatus)
}