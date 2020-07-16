package uk.gov.justice.digital.hmpps.indexer.resource

import arrow.core.getOrHandle
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.service.BuildIndexError
import uk.gov.justice.digital.hmpps.indexer.service.CancelBuildIndexError
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.MarkBuildCompleteError

@Api(tags = ["probation-index"])
@RestController
@RequestMapping("/probation-index", produces = [MediaType.APPLICATION_JSON_VALUE])
class IndexResource(private val indexService: IndexService) {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/build-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @ApiOperation(
      value = "Start building a new index",
      notes = "The current index will be left untouched and continue to be maintained while the new index is built.  The new index must not be currently building.  Requires PROBATION_INDEX role.  Returns the new status of the index.")
  @ApiResponses(value = [
    ApiResponse(code = 200, message = "OK", response = IndexStatus::class),
    ApiResponse(code = 401, message = "Unauthorised, requires a valid Oauth2 token"),
    ApiResponse(code = 403, message = "Forbidden, requires an authorisation with role PROBATION_INDEX"),
    ApiResponse(code = 409, message = "Conflict, the index was not in a state to start building")
  ])
  fun buildIndex(): IndexStatus =
      indexService.prepareIndexForRebuild()
          .getOrHandle { error ->
            when (error) {
              is BuildIndexError.BuildAlreadyInProgress -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message)
            }
          }

  @ApiOperation(
      value = "Mark the current index build as complete",
      notes = "Completes the index build if it is currently building.  Requires PROBATION_INDEX role.  Returns the new status of the index.")
  @ApiResponses(value = [
    ApiResponse(code = 200, message = "OK", response = IndexStatus::class),
    ApiResponse(code = 401, message = "Unauthorised, requires a valid Oauth2 token"),
    ApiResponse(code = 403, message = "Forbidden, requires an authorisation with role PROBATION_INDEX"),
    ApiResponse(code = 409, message = "Conflict, the index was not currently building")
  ])
  @PutMapping("/mark-complete")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun markComplete() =
      indexService.markIndexingComplete()
          .getOrHandle { error ->
            when (error) {
              is MarkBuildCompleteError.BuildNotInProgress -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message)
            }
          }

  @PutMapping("/cancel-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @ApiOperation(
      value = "Cancel building an index",
      notes = "Cancels the building of the current index if it is currently building.  Requires PROBATION_INDEX role.  Returns the new status of the index.")
  @ApiResponses(value = [
    ApiResponse(code = 200, message = "OK", response = IndexStatus::class),
    ApiResponse(code = 401, message = "Unauthorised, requires a valid Oauth2 token"),
    ApiResponse(code = 403, message = "Forbidden, requires an authorisation with role PROBATION_INDEX"),
    ApiResponse(code = 409, message = "Conflict, the index was not currently building")
  ])
  fun cancelIndex() =
      indexService.cancelIndexing()
          .getOrHandle { error ->
            when (error) {
              is CancelBuildIndexError.BuildNotInProgress -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message)
            }
          }

  @PutMapping("/index/offender/{crn}")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @ApiOperation(
      value = "Add or refresh the offender in the current index",
      notes = "Requires PROBATION_INDEX role.  Returns the offender details added to the index.")
  @ApiResponses(value = [
    ApiResponse(code = 200, message = "OK", response = String::class),
    ApiResponse(code = 401, message = "Unauthorised, requires a valid Oauth2 token"),
    ApiResponse(code = 403, message = "Forbidden, requires an authorisation with role PROBATION_INDEX")
  ])
  fun indexOffender(@PathVariable("crn") crn: String) = indexService.indexOffender(crn)

}