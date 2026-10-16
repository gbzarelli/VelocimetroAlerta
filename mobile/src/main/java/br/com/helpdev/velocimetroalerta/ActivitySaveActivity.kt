package br.com.helpdev.velocimetroalerta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.AppCompatButton

class ActivitySaveActivity : AppCompatActivity() {
    private lateinit var nameInput: EditText
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge.enable(window)
        setContentView(R.layout.activity_save_activity)
        EdgeToEdge.applySystemBarInsets(window, findViewById(R.id.activity_save_root))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        nameInput = findViewById(R.id.activity_name)
        errorText = findViewById(R.id.activity_name_error)
        nameInput.setText(intent.getStringExtra(EXTRA_SUGGESTED_NAME).orEmpty())
        nameInput.setSelection(nameInput.text.length)

        findViewById<AppCompatButton>(R.id.complete_activity).setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                errorText.visibility = android.view.View.VISIBLE
                nameInput.requestFocus()
                return@setOnClickListener
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACTION, ACTION_CONFIRM).putExtra(EXTRA_NAME, name))
            finish()
        }
        findViewById<AppCompatButton>(R.id.discard_activity).setOnClickListener {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACTION, ACTION_DISCARD))
            finish()
        }
        nameInput.addTextChangedListener(TextWatcherAdapter { errorText.visibility = android.view.View.GONE })
    }

    companion object {
        const val EXTRA_SUGGESTED_NAME = "activity_save.suggested_name"
        const val EXTRA_ACTION = "activity_save.action"
        const val EXTRA_NAME = "activity_save.name"
        const val ACTION_CONFIRM = "confirm"
        const val ACTION_DISCARD = "discard"

        fun newIntent(context: Context, suggestedName: String) =
            Intent(context, ActivitySaveActivity::class.java).putExtra(EXTRA_SUGGESTED_NAME, suggestedName)
    }
}

private class TextWatcherAdapter(private val onChanged: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onChanged()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
