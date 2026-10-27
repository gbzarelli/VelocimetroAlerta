package br.com.helpdev.velocimetroalerta.strava

internal sealed class StravaUploadHttpFailure {
    data class Rejected(val statusCode: Int, val message: String?) : StravaUploadHttpFailure()
    object Unauthorized : StravaUploadHttpFailure()
    object RateLimited : StravaUploadHttpFailure()
    object Ambiguous : StravaUploadHttpFailure()
}

internal fun classifyStravaUploadHttpFailure(
    statusCode: Int,
    responseBody: String
): StravaUploadHttpFailure = when {
    statusCode == 401 -> StravaUploadHttpFailure.Unauthorized
    statusCode == 429 -> StravaUploadHttpFailure.RateLimited
    statusCode == 408 || statusCode == 409 -> StravaUploadHttpFailure.Ambiguous
    statusCode in 500..599 -> StravaUploadHttpFailure.Ambiguous
    statusCode in 400..499 && responseBody.contains("duplicate", ignoreCase = true) ->
        StravaUploadHttpFailure.Ambiguous
    statusCode in 400..499 -> StravaUploadHttpFailure.Rejected(statusCode, extractMessage(responseBody))
    else -> StravaUploadHttpFailure.Ambiguous
}

private fun extractMessage(responseBody: String): String? {
    val encodedMessage = Regex("\\\"message\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        ?: return null

    return encodedMessage
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(160)
        .takeIf(String::isNotBlank)
}
