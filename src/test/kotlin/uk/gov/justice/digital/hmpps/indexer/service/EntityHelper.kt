package uk.gov.justice.digital.hmpps.indexer.service

import com.google.gson.Gson


fun anOffender(crn: String = "X12345", offenderId: Long = 99) = Offender(Gson().toJson(OffenderDetail(crn, offenderId)))


