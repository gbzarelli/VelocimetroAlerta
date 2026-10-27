package br.com.helpdev.velocimetroalerta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.Toolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import br.com.helpdev.velocimetroalerta.auth.AccountSession
import br.com.helpdev.velocimetroalerta.auth.GoogleAuthRepository
import br.com.helpdev.velocimetroalerta.settings.LocalProfileStore
import br.com.helpdev.velocimetroalerta.settings.SettingsSyncRepository
import br.com.helpdev.velocimetroalerta.settings.SyncableSettings
import br.com.helpdev.velocimetroalerta.strava.PendingManualActivityStore
import br.com.helpdev.velocimetroalerta.strava.StravaConnectionRepository
import br.com.helpdev.velocimetroalerta.strava.StravaConnectionState
import br.com.helpdev.velocimetroalerta.strava.StravaSyncCoordinator

class AccountConnectionsActivity : AppCompatActivity() {
    private lateinit var googleAuth: GoogleAuthRepository
    private lateinit var profileStore: LocalProfileStore
    private lateinit var settingsSync: SettingsSyncRepository
    private lateinit var strava: StravaConnectionRepository
    private lateinit var pendingManualStore: PendingManualActivityStore
    private lateinit var accountName: TextView
    private lateinit var accountDescription: TextView
    private lateinit var googleButton: AppCompatButton
    private lateinit var stravaStatus: TextView
    private lateinit var stravaButton: AppCompatButton
    private lateinit var autoSync: SwitchMaterial
    private lateinit var autoSyncDescription: TextView
    private lateinit var accountContent: View
    private lateinit var loading: View
    private var settingsReadyUid: String? = null
    private var settingsLoadingUid: String? = null
    private var lastConnection = StravaConnectionState(connected = false)
    private var connectionLoaded = false
    private var connectionRequestInFlight = false
    private var connectionRequestUid: String? = null
    private var pendingConnectAttempted = false
    private var pendingWasScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_connections)
        findViewById<Toolbar>(R.id.toolbar).apply {
            setTitle(R.string.account_title)
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
        }
        accountName = findViewById(R.id.account_name)
        accountDescription = findViewById(R.id.account_description)
        googleButton = findViewById(R.id.account_google_button)
        stravaStatus = findViewById(R.id.account_strava_status)
        stravaButton = findViewById(R.id.account_strava_button)
        autoSync = findViewById(R.id.account_auto_sync)
        autoSyncDescription = findViewById(R.id.account_auto_sync_description)
        accountContent = findViewById(R.id.account_connections_content)
        loading = findViewById(R.id.account_connections_loading)

        googleAuth = GoogleAuthRepository(this)
        profileStore = LocalProfileStore(this)
        settingsSync = (application as VelocimetroApplication).settingsSyncRepository
        strava = StravaConnectionRepository(this)
        pendingManualStore = PendingManualActivityStore(this)
        intent.getStringExtra(EXTRA_PENDING_ACTIVITY_ID)?.let { activityId ->
            pendingManualStore.save(activityId, googleAuth.currentSession()?.uid)
        }
        pendingConnectAttempted = savedInstanceState?.getBoolean(KEY_PENDING_CONNECT_ATTEMPTED) == true
        pendingWasScheduled = savedInstanceState?.getBoolean(KEY_PENDING_SCHEDULED) == true

        googleButton.setOnClickListener { onGoogleButtonClicked() }
        stravaButton.setOnClickListener { onStravaButtonClicked() }
        autoSync.setOnCheckedChangeListener { _, checked ->
            if (autoSync.isEnabled) {
                settingsSync.setStravaAutoSyncEnabled(checked)
                if (!checked) StravaSyncCoordinator.cancelQueuedAutomatic(this)
            }
        }
        refreshAccount()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_PENDING_CONNECT_ATTEMPTED, pendingConnectAttempted)
        outState.putBoolean(KEY_PENDING_SCHEDULED, pendingWasScheduled)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        refreshAccount()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshAccount() {
        val session = googleAuth.currentSession()
        if (session == null) {
            settingsSync.stop()
            settingsReadyUid = null
            settingsLoadingUid = null
            accountName.text = getString(R.string.account_guest)
            accountDescription.text = getString(R.string.account_google_optional)
            googleButton.setText(R.string.google_sign_in)
            googleButton.isEnabled = true
            setStravaUi(StravaConnectionState(connected = false))
            connectionLoaded = false
            return
        }
        accountName.text = session.displayName?.takeIf(String::isNotBlank) ?: session.email ?: session.uid
        accountDescription.text = getString(R.string.account_signed_in_as, accountName.text)
        googleButton.setText(R.string.account_sign_out)
        if (settingsReadyUid != session.uid && settingsLoadingUid != session.uid) {
            if (settingsSync.isTracking(session.uid)) {
                settingsSync.start(session.uid)
                settingsReadyUid = session.uid
                processPendingManual(session)
            } else if (profileStore.hasAccount(session.uid)) {
                profileStore.restoreAccount(session.uid)
                settingsSync.start(session.uid)
                settingsReadyUid = session.uid
                processPendingManual(session)
            } else loadSettings(session)
        }
        refreshStravaState(session)
    }

    private fun loadSettings(session: AccountSession) {
        settingsLoadingUid = session.uid
        loading.visibility = View.VISIBLE
        accountContent.visibility = View.GONE
        settingsSync.loadInitial(session.uid) { result ->
            if (isFinishing) return@loadInitial
            loading.visibility = View.GONE
            accountContent.visibility = View.VISIBLE
            result.onSuccess { remote ->
                if (remote == null) {
                    settingsSync.firstLogin(session.uid) { writeResult ->
                        writeResult.onSuccess {
                            settingsLoadingUid = null
                            settingsReadyUid = session.uid
                            profileStore.captureAccount(session.uid)
                            renderAutoSync()
                            processPendingManual(session)
                        }.onFailure {
                            settingsLoadingUid = null
                            showSettingsError()
                        }
                    }
                } else {
                    showProfileChoice(session, remote)
                }
            }.onFailure { failure ->
                settingsLoadingUid = null
                Toast.makeText(this, failure.message ?: getString(R.string.account_settings_sync_error), Toast.LENGTH_LONG).show()
                accountContent.visibility = View.VISIBLE
            }
        }
    }

    private fun showProfileChoice(session: AccountSession, remote: Map<String, Any>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.account_restore_cloud_title)
            .setMessage(R.string.account_restore_cloud_message)
            .setPositiveButton(R.string.account_restore_cloud) { _, _ ->
                settingsSync.restoreRemote(session.uid, remote)
                settingsLoadingUid = null
                settingsReadyUid = session.uid
                profileStore.captureAccount(session.uid)
                renderAutoSync()
                processPendingManual(session)
            }
            .setNegativeButton(R.string.account_use_device) { _, _ ->
                settingsSync.useLocalProfile(session.uid) { result ->
                    result.onSuccess {
                        settingsLoadingUid = null
                        settingsReadyUid = session.uid
                        profileStore.captureAccount(session.uid)
                        renderAutoSync()
                        processPendingManual(session)
                    }.onFailure {
                        settingsLoadingUid = null
                        showSettingsError()
                    }
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun onGoogleButtonClicked() {
        val current = googleAuth.currentSession()
        if (current != null) {
            AlertDialog.Builder(this)
                .setMessage(R.string.account_sign_out)
                .setNegativeButton(R.string.bt_cancel, null)
                .setPositiveButton(R.string.account_sign_out) { _, _ -> signOut(current.uid) }
                .show()
            return
        }
        profileStore.captureGuest()
        googleButton.isEnabled = false
        googleAuth.signIn(this) { result ->
            googleButton.isEnabled = true
            result.onSuccess { session ->
                settingsReadyUid = null
                refreshAccount()
                if (session.uid == googleAuth.currentSession()?.uid) processPendingManual(session)
            }.onFailure { error ->
                if (error !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    Toast.makeText(this, error.message ?: getString(R.string.google_sign_in_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun signOut(uid: String) {
        profileStore.captureAccount(uid)
        settingsSync.stop()
        googleAuth.signOut(this)
        strava.clearLocalState(uid)
        settingsReadyUid = null
        settingsLoadingUid = null
        profileStore.restoreGuest()
        refreshAccount()
    }

    private fun refreshStravaState(session: AccountSession) {
        if (connectionRequestInFlight && connectionRequestUid == session.uid) return
        if (connectionRequestUid != session.uid) connectionLoaded = false
        connectionRequestInFlight = true
        connectionRequestUid = session.uid
        strava.getState { result ->
            if (connectionRequestUid == session.uid) connectionRequestInFlight = false
            if (isFinishing || googleAuth.currentSession()?.uid != session.uid) return@getState
            result.onSuccess { state ->
                lastConnection = state
                connectionLoaded = true
                setStravaUi(state)
                renderAutoSync()
                processPendingManual(session)
            }.onFailure {
                lastConnection = StravaConnectionState(connected = false, reconnectRequired = true)
                connectionLoaded = true
                setStravaUi(lastConnection)
            }
        }
    }

    private fun setStravaUi(state: StravaConnectionState) {
        stravaStatus.text = when {
            state.connected -> getString(R.string.strava_connected_as, state.athleteName ?: getString(R.string.account_strava))
            state.reconnectRequired -> getString(R.string.strava_reconnect_required)
            else -> getString(R.string.strava_not_connected)
        }
        stravaButton.setText(if (state.connected) R.string.strava_disconnect else R.string.strava_connect)
        stravaButton.isEnabled = googleAuth.currentSession() != null
        renderAutoSync()
    }

    private fun renderAutoSync() {
        val connected = lastConnection.connected && googleAuth.currentSession() != null
        autoSync.setOnCheckedChangeListener(null)
        autoSync.isChecked = SyncableSettings.snapshot(this)[SyncableSettings.AUTO_SYNC_KEY] as? Boolean ?: false
        autoSync.isEnabled = connected
        autoSync.setOnCheckedChangeListener { _, checked ->
            if (autoSync.isEnabled) {
                settingsSync.setStravaAutoSyncEnabled(checked)
                if (!checked) StravaSyncCoordinator.cancelQueuedAutomatic(this)
            }
        }
        autoSyncDescription.setText(if (connected) R.string.strava_auto_sync_help else R.string.strava_auto_sync_needs_connection)
    }

    private fun onStravaButtonClicked() {
        if (googleAuth.currentSession() == null) {
            Toast.makeText(this, R.string.strava_requires_google, Toast.LENGTH_LONG).show()
            return
        }
        if (lastConnection.connected) {
            AlertDialog.Builder(this)
                .setMessage(R.string.strava_disconnect_confirm)
                .setNegativeButton(R.string.bt_cancel, null)
                .setPositiveButton(R.string.strava_disconnect) { _, _ -> disconnectStrava() }
                .show()
        } else beginStravaConnect()
    }

    private fun beginStravaConnect() {
        stravaButton.isEnabled = false
        strava.beginConnect { result ->
            stravaButton.isEnabled = true
            result.onSuccess { url ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { Toast.makeText(this, R.string.strava_oauth_failed, Toast.LENGTH_LONG).show() }
            }.onFailure { Toast.makeText(this, it.message ?: getString(R.string.strava_oauth_failed), Toast.LENGTH_LONG).show() }
        }
    }

    private fun disconnectStrava() {
        stravaButton.isEnabled = false
        strava.disconnect { result ->
            stravaButton.isEnabled = true
            result.onSuccess {
                lastConnection = StravaConnectionState(connected = false)
                settingsSync.setStravaAutoSyncEnabled(false)
                StravaSyncCoordinator.cancelQueuedAutomatic(this)
                setStravaUi(lastConnection)
            }.onFailure { Toast.makeText(this, R.string.strava_disconnect_failed, Toast.LENGTH_LONG).show() }
        }
    }

    private fun processPendingManual(session: AccountSession) {
        if (pendingWasScheduled) return
        if (!pendingManualStore.bindTo(session.uid)) {
            pendingManualStore.clear()
            return
        }
        val pendingId = pendingManualStore.peek(session.uid) ?: return
        if (googleAuth.currentSession()?.uid != session.uid || settingsReadyUid != session.uid || !connectionLoaded) return
        if (!lastConnection.connected) {
            if (!pendingConnectAttempted) {
                pendingConnectAttempted = true
                beginStravaConnect()
            }
            return
        }
        pendingWasScheduled = true
        StravaSyncCoordinator.enqueueManual(this, session.uid, pendingId)
        pendingManualStore.clear()
        finish()
    }

    private fun showSettingsError() {
        Toast.makeText(this, R.string.account_settings_sync_error, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_PENDING_ACTIVITY_ID = "account.pending_activity_id"
        private const val KEY_PENDING_CONNECT_ATTEMPTED = "account.pending_connect_attempted"
        private const val KEY_PENDING_SCHEDULED = "account.pending_scheduled"
    }
}
