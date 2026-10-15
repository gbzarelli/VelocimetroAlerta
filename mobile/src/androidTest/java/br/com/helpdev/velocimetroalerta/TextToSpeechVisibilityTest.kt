package br.com.helpdev.velocimetroalerta

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class TextToSpeechVisibilityTest {
    @Test
    fun defaultTextToSpeechEngineCanBeDiscoveredAndSpeak() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engines = context.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0
        )
        assertTrue("The app must be able to discover an installed TTS engine", engines.isNotEmpty())

        val initialized = CountDownLatch(1)
        val utteranceFinished = CountDownLatch(1)
        val initStatus = AtomicInteger(TextToSpeech.ERROR)
        val utteranceStatus = AtomicInteger(TextToSpeech.SUCCESS)
        val tts = TextToSpeech(context) { status ->
            initStatus.set(status)
            initialized.countDown()
        }
        try {
            assertTrue("TTS initialization timed out", initialized.await(10, TimeUnit.SECONDS))
            assertEquals("TTS engine initialization failed", TextToSpeech.SUCCESS, initStatus.get())
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { utteranceFinished.countDown() }
                override fun onError(utteranceId: String?) {
                    utteranceStatus.set(TextToSpeech.ERROR)
                    utteranceFinished.countDown()
                }
            })
            val result = tts.speak(
                "Tracking started",
                TextToSpeech.QUEUE_FLUSH,
                null,
                UUID.randomUUID().toString()
            )
            assertEquals("TTS rejected the utterance", TextToSpeech.SUCCESS, result)
            assertTrue("The utterance did not finish", utteranceFinished.await(15, TimeUnit.SECONDS))
            assertEquals("TTS failed while speaking", TextToSpeech.SUCCESS, utteranceStatus.get())
        } finally {
            tts.shutdown()
        }
    }
}
