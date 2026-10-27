package br.com.helpdev.velocimetroalerta.strava

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.helpdev.velocimetroalerta.R

class StravaOAuthCallbackActivity : AppCompatActivity() {
    private var requestHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHandled = savedInstanceState?.getBoolean(KEY_HANDLED) == true
        if (!requestHandled) handleCallback(intent?.data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestHandled = false
        handleCallback(intent.data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_HANDLED, requestHandled)
        super.onSaveInstanceState(outState)
    }

    private fun handleCallback(uri: Uri?) {
        if (requestHandled) return
        requestHandled = true
        if (uri == null) {
            Toast.makeText(this, R.string.strava_oauth_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val state = uri.getQueryParameter("state").orEmpty()
        val error = uri.getQueryParameter("error")
        if (state.isBlank()) {
            Toast.makeText(this, R.string.strava_oauth_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val repository = StravaConnectionRepository(this)
        if (error != null) {
            repository.cancelConnect(state) {
                if (error == "access_denied") Toast.makeText(this, R.string.strava_oauth_cancelled, Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }
        val code = uri.getQueryParameter("code").orEmpty()
        if (code.isBlank()) {
            repository.cancelConnect(state) {
                Toast.makeText(this, R.string.strava_oauth_failed, Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }
        repository.completeConnect(code, state) { result ->
            result.onSuccess { setResult(Activity.RESULT_OK) }
                .onFailure { Toast.makeText(this, R.string.strava_oauth_failed, Toast.LENGTH_LONG).show() }
            finish()
        }
    }

    private companion object {
        const val KEY_HANDLED = "strava_callback_handled"
    }
}
