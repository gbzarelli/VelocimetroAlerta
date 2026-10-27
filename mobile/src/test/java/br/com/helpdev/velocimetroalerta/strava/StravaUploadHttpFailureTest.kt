package br.com.helpdev.velocimetroalerta.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StravaUploadHttpFailureTest {
    @Test
    fun clientErrorIsReportedAsDefinitiveRejection() {
        val failure = classifyStravaUploadHttpFailure(
            statusCode = 400,
            responseBody = """{"message":"Bad Request"}"""
        )

        assertEquals(StravaUploadHttpFailure.Rejected(400, "Bad Request"), failure)
    }

    @Test
    fun duplicateResponseRemainsAmbiguous() {
        val failure = classifyStravaUploadHttpFailure(409, "Duplicate activity")

        assertEquals(StravaUploadHttpFailure.Ambiguous, failure)
        assertEquals(StravaUploadHttpFailure.Ambiguous, classifyStravaUploadHttpFailure(409, "Conflict"))
        assertEquals(StravaUploadHttpFailure.Ambiguous, classifyStravaUploadHttpFailure(408, "Request timeout"))
    }

    @Test
    fun authenticationRateLimitAndServerErrorsKeepTheirSpecialHandling() {
        assertEquals(StravaUploadHttpFailure.Unauthorized, classifyStravaUploadHttpFailure(401, ""))
        assertEquals(StravaUploadHttpFailure.RateLimited, classifyStravaUploadHttpFailure(429, ""))
        assertEquals(StravaUploadHttpFailure.Ambiguous, classifyStravaUploadHttpFailure(503, ""))
    }

    @Test
    fun rejectionMessageIsBoundedAndSingleLine() {
        val body = """{"message":"${"bad ".repeat(100)}request"}"""
        val failure = classifyStravaUploadHttpFailure(422, body)

        val rejected = failure as? StravaUploadHttpFailure.Rejected
            ?: throw AssertionError("Expected a definitive Strava rejection")
        assertTrue(rejected.message!!.length <= 160)
        assertTrue(!rejected.message!!.contains('\n'))
    }
}
