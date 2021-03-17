package uk.gov.justice.digital.hmpps.indexer.resource

import arrow.core.getOrHandle
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import uk.gov.justice.digital.hmpps.indexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.indexer.service.CancelBuildError
import uk.gov.justice.digital.hmpps.indexer.service.IndexService
import uk.gov.justice.digital.hmpps.indexer.service.MarkCompleteError
import uk.gov.justice.digital.hmpps.indexer.service.PrepareRebuildError
import uk.gov.justice.digital.hmpps.indexer.service.QueueAdminService
import uk.gov.justice.digital.hmpps.indexer.service.UpdateOffenderError

@RestController
@RequestMapping("/probation-index", produces = [MediaType.APPLICATION_JSON_VALUE])
class IndexResource(
  private val indexService: IndexService,
  private val queueAdminService: QueueAdminService
) {

  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping("/build-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @Operation(
    summary = "Start building a new index",
    description = "The current index will be left untouched and continue to be maintained while the new index is built.  The new index must not be currently building.  Requires PROBATION_INDEX role.  Returns the new status of the index."
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX"),
      ApiResponse(responseCode = "409", description = "Conflict, the index was not in a state to start building")
    ]
  )
  fun buildIndex(): IndexStatus =
    indexService.prepareIndexForRebuild()
      .getOrHandle { error ->
        log.error("Request to /probation-index/build-index failed due to error {}", error)
        when (PrepareRebuildError.fromErrorClass(error)) {
          PrepareRebuildError.BUILD_IN_PROGRESS -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message())
          PrepareRebuildError.ACTIVE_MESSAGES_EXIST -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message())
        }
      }

  @Operation(
    summary = "Mark the current index build as complete",
    description = "Completes the index build if it is currently building.  Requires PROBATION_INDEX role.  Returns the new status of the index."
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX"),
      ApiResponse(responseCode = "409", description = "Conflict, the index was not currently building")
    ]
  )
  @PutMapping("/mark-complete")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  fun markComplete(@RequestParam(name = "ignoreThreshold", required = false) ignoreThreshold: Boolean = false) =
    indexService.markIndexingComplete(ignoreThreshold)
      .getOrHandle { error ->
        log.error("Request to /probation-index/mark-complete failed due to error {}", error)
        when (MarkCompleteError.fromErrorClass(error)) {
          MarkCompleteError.BUILD_NOT_IN_PROGRESS -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message())
          MarkCompleteError.ACTIVE_MESSAGES_EXIST -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message())
          MarkCompleteError.THRESHOLD_NOT_REACHED -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message())
        }
      }

  @PutMapping("/cancel-index")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @Operation(
    summary = "Cancel building an index",
    description = "Cancels the building of the current index if it is currently building.  Requires PROBATION_INDEX role.  Returns the new status of the index."
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX"),
      ApiResponse(responseCode = "409", description = "Conflict, the index was not currently building")
    ]
  )
  fun cancelIndex() =
    indexService.cancelIndexing()
      .getOrHandle { error ->
        log.error("Request to /probation-index/cancel-index failed due to error {}", error)
        when (CancelBuildError.fromErrorClass(error)) {
          CancelBuildError.BUILD_NOT_IN_PROGRESS -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message())
        }
      }

  @PutMapping("/index/offender/{crn}")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @Operation(
    summary = "Add or refresh the offender in the current index",
    description = "Requires PROBATION_INDEX role.  Returns the offender details added to the index."
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX"),
      ApiResponse(responseCode = "404", description = "Not Found, the offender could not be found"),
      ApiResponse(responseCode = "409", description = "Conflict, no indexes could be updated")
    ]
  )
  fun indexOffender(@PathVariable("crn") crn: String) = indexService.updateOffender(crn)
    .getOrHandle { error ->
      log.error("Request to /probation-index/index/offender/$crn failed due to error {}", error)
      when (UpdateOffenderError.fromErrorClass(error)) {
        UpdateOffenderError.NO_ACTIVE_INDEXES -> throw ResponseStatusException(HttpStatus.CONFLICT, error.message())
        UpdateOffenderError.OFFENDER_NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND, error.message())
      }
    }

  @PutMapping("/purge-index-dlq")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @Operation(
    summary = "Purges the index dead letter queue",
    description = "Requires PROBATION_INDEX role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX")
    ]
  )
  fun purgeIndexDlq(): Unit = queueAdminService.clearAllDlqMessagesForIndex()

  @PutMapping("/purge-event-dlq")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @Operation(
    summary = "Purges the event dead letter queue",
    description = "Requires PROBATION_INDEX role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX")
    ]
  )
  fun purgeEventDlq(): Unit = queueAdminService.clearAllDlqMessagesForEvent()

  @PutMapping("/transfer-index-dlq")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @Operation(
    summary = "Transfers all DLQ messages to the main queue",
    description = "Requires PROBATION_INDEX role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX")
    ]
  )
  fun transferIndexDlq(): Unit = queueAdminService.transferIndexMessages()

  @PutMapping("/transfer-event-dlq")
  @PreAuthorize("hasRole('PROBATION_INDEX')")
  @Operation(
    summary = "Transfers all DLQ messages to the main queue",
    description = "Requires PROBATION_INDEX role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role PROBATION_INDEX")
    ]
  )
  fun transferEventDlq(): Unit = queueAdminService.transferEventMessages()

  @PutMapping("/queue-housekeeping")
  @Operation(
    summary = "Triggers maintenance of the index queue",
    description = "This is an internal service which isn't exposed to the outside world. It is called from a Kubernetes CronJob named `index-housekeeping-cronjob`"
  )
  fun indexQueueHousekeeping() {
    indexService.markIndexingComplete(ignoreThreshold = false)
    queueAdminService.transferIndexMessages()
    queueAdminService.transferEventMessages()
  }
}
