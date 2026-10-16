package br.com.helpdev.velocimetroalerta

import android.app.Activity
import com.google.android.play.core.review.ReviewManager

fun interface InAppReviewRequester {
    fun request(activity: Activity)
}

class InAppReviewLauncher(
    private val reviewManager: ReviewManager,
    private val onUnavailable: () -> Unit
) : InAppReviewRequester {

    override fun request(activity: Activity) {
        reviewManager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                onUnavailable()
                return@addOnCompleteListener
            }

            reviewManager.launchReviewFlow(activity, request.result).addOnCompleteListener { flow ->
                if (!flow.isSuccessful) onUnavailable()
            }
        }
    }
}
