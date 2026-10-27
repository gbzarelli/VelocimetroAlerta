package br.com.helpdev.velocimetroalerta

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

/** Firebase remains optional for local-only use until an environment supplies google-services.json. */
object FirebaseServices {
    fun app(context: Context): FirebaseApp? = FirebaseApp.getApps(context).firstOrNull()

    fun auth(context: Context): FirebaseAuth? = app(context)?.let { FirebaseAuth.getInstance(it) }

    fun firestore(context: Context): FirebaseFirestore? = app(context)?.let { FirebaseFirestore.getInstance(it) }

    fun functions(context: Context): FirebaseFunctions? = app(context)?.let {
        FirebaseFunctions.getInstance(it, "southamerica-east1")
    }
}
