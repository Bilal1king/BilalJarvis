package com.bilal.jarvis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class OrbView(c: Context) : View(c) {
    var state = 0
    private var ph = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols = intArrayOf(
        Color.rgb(34, 211, 238), Color.rgb(245, 196, 81),
        Color.rgb(139, 92, 246), Color.rgb(34, 211, 238)
    )

    override fun onDraw(cv: Canvas) {
        super.onDraw(cv)
        ph += 0.02f
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) / 520f
        val col = cols[state]
        val r = Color.red(col)
        val g = Color.green(col)
        val b = Color.blue(col)
        val amp = when (state) {
            3 -> sin(ph * 9f) * 10f + 10f
            1 -> sin(ph * 4f) * 6f + 6f
            2 -> 4f
            else -> 2f
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * s
        for (i in 0..2) {
            val dir = if (i % 2 == 0) 1f else -1f
            val speed = (0.4f + i * 0.25f) * (if (state == 2) 3f else 1f)
            cv.save()
            cv.translate(cx, cy)
            cv.rotate(ph * dir * speed * 57.2958f)
            cv.scale(1f, 0.45f + i * 0.2f)
            p.color = Color.argb((255 * (0.7f - i * 0.15f)).toInt(), r, g, b)
            cv.drawCircle(0f, 0f, (120f + i * 28f + amp) * s, p)
            cv.restore()
        }
        p.style = Paint.Style.FILL
        val rad = (95f + amp) * s
        p.shader = RadialGradient(
            cx, cy, rad,
            intArrayOf(Color.argb(240, r, g, b), Color.argb(128, 139, 92, 246), Color.argb(0, 7, 11, 26)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(cx, cy, rad, p)
        p.shader = null
        for (i in 0 until 18) {
            val a = ph * 0.8f + i * 0.349f
            val d = (150f + sin(ph * 2f + i) * 14f) * s
            p.color = if (i % 3 != 0) Color.rgb(34, 211, 238) else Color.rgb(245, 196, 81)
            val x = cx + cos(a) * d
            val y = cy + sin(a) * d * 0.8f
            cv.drawRect(x, y, x + 4f * s, y + 4f * s, p)
        }
        p.color = Color.WHITE
        p.textSize = 56f * s
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        cv.drawText("BF", cx, cy + 20f * s, p)
        postInvalidateOnAnimation()
    }
}

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var orb: OrbView
    private lateinit var scroll: ScrollView
    private var sr: SpeechRecognizer? = null
    private var ttsReady = false
    private var session = false
    private var busy = false
    private var confirming = false
    private var female = true
    private val h = Handler(Looper.getMainLooper())

    private fun dp(x: Int): Int = (x * resources.displayMetrics.density).toInt()

    private fun btn(t: String, f: () -> Unit): Button {
        val bt = Button(this)
        bt.text = t
        bt.setTextColor(Color.WHITE)
        bt.setBackgroundColor(Color.parseColor("#1b2a5a"))
        bt.setOnClickListener { f() }
        return bt
    }

    private fun row(vararg v: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (x in v) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            r.addView(x, lp)
        }
        return r
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#070b1a"))
        root.setPadding(dp(16), dp(40), dp(16), dp(16))

        val title = TextView(this)
        title.text = "B I L A L   J A R V I S   A I"
        title.textSize = 14f
        title.setTextColor(Color.parseColor("#22d3ee"))
        title.gravity = Gravity.CENTER
        root.addView(title)

        orb = OrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)))

        status = TextView(this)
        status.text = "Idle"
        status.textSize = 14f
        status.setTextColor(Color.parseColor("#f5c451"))
        status.gravity = Gravity.CENTER
        root.addView(status)

        root.addView(row(btn("🎙 Start voice session") { startSession() }, btn("Stop") { stopSession() }))
        root.addView(row(btn("Speaker ON") { speaker(true) }, btn("Speaker OFF") { speaker(false) }))

        log = TextView(this)
        log.textSize = 15f
        log.setTextColor(Color.WHITE)
        log.setPadding(dp(8), dp(8), dp(8), dp(8))
        log.text = "Assalam-o-Alaikum Bilal ❤️ Main tumhara JARVIS hoon. Start voice session dabao aur bolo: battery, time, YouTube kholo, Abu ko call karo, speaker on, female voice, male voice, bas."
        scroll = ScrollView(this)
        scroll.addView(log)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 2
        )
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { runOnUiThread { setState(3, "Speaking…") } }
                override fun onDone(id: String?) { runOnUiThread { done() } }
                override fun onError(id: String?) { runOnUiThread { done() } }
            })
            ttsReady = true
        }
    }

    private fun setState(s: Int, t: String) { orb.state = s; status.text = t }

    private fun addLog(t: String) {
        log.append("\n\n" + t)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun done() {
        if (!busy) return
        busy = false
        setState(0, if (session) "Listening…" else "Idle")
        if (session) h.postDelayed({ listen() }, 300)
    }

    private fun say(t: String) {
        addLog("Jarvis: $t")
        if (!ttsReady) { if (session) h.postDelayed({ listen() }, 500); return }
        busy = true
        tts.setPitch(if (female) 1.2f else 0.8f)
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j")
        h.postDelayed({ if (busy) done() }, t.length * 90L + 5000L)
    }

    private fun startSession() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
            addLog("Mic ki permission allow karo, phir dobara Start dabao.")
            return
        }
        if (session) return
        session = true
        listen()
    }

    private fun stopSession() {
        session = false
        busy = false
        try { sr?.cancel(); sr?.destroy() } catch (e: Exception) { }
        sr = null
        tts.stop()
        setState(0, "Idle")
    }

    private fun retry() { if (session) h.postDelayed({ listen() }, 700) }

    private fun listen() {
        if (!session || busy || confirming) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            session = false
            addLog("Is phone mein speech recognition available nahi hai.")
            return
        }
        try { sr?.destroy() } catch (e: Exception) { }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { setState(2, "Thinking…") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t == null) { retry(); return }
                addLog("Bilal: $t")
                handle(t.lowercase())
            }
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> retry()
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        stopSession(); say("Internet check karo Bilal, awaaz samajhne mein dikkat aa rahi hai.")
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stopSession(); addLog("Mic ki permission chahiye.") }
                    else -> { stopSession(); addLog("Voice error: $error") }
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        setState(1, "Listening…")
        r.startListening(i)
    }

    private fun handle(t: String) {
        when {
            t.contains("chup ho") || t.contains("listening band") || t.contains("stop") || t.trim() == "bas" -> {
                stopSession(); say("Theek hai Bilal, listening band kar di.")
            }
            t.contains("female voice") -> { female = true; say("Female voice chalu kar di, Bilal.") }
            t.contains("male voice") -> { female = false; say("Male voice chalu kar di, Bilal.") }
            t.contains("battery") -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                say("Bilal, battery $pct percent hai" + (if (bm.isCharging) " aur charge ho rahi hai." else "."))
            }
            t.contains("time") || t.contains("waqt") || t.contains("baje") ->
                say("Abhi " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) + " ho rahe hain.")
            t.contains("youtube") -> {
                val i = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (i != null) { startActivity(i); say("YouTube khol raha hoon.") }
                else { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))); say("YouTube app nahi mili, browser mein khol diya.") }
            }
            t.contains("speaker") -> speaker(t.contains(" on") || t.contains("chalu") || t.contains("lagao"))
            t.contains("call") -> callByName(t)
            else -> say("Bilal, yeh command abhi samajh nahi aayi. Battery, time, YouTube, speaker ya call try karo.")
        }
    }

    private fun speaker(on: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = on
        say("Speaker " + (if (on) "on" else "off") + " kar diya. Agar call mein awaaz na badle, to call screen ka speaker button bhi check karna.")
    }

    private fun callByName(t: String) {
        val name = t.replace(Regex("\\b(jarvis|call|karo|lagao|ko|kijiye|please|ab|mujhe|mere)\\b"), "").trim()
        if (name.isEmpty()) { say("Kisko call karni hai Bilal?"); return }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            say("Contacts ki permission chahiye. Settings mein allow kar do."); return
        }
        var found: Pair<String, String>? = null
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null
        )?.use {
            if (it.moveToFirst()) found = Pair(it.getString(0), it.getString(1))
        }
        val f = found
        if (f == null) { say("Bilal, contacts mein $name nahi mila."); return }
        say("${f.first} ke number par call lagane wala hoon. Kya call kar doon?")
        confirming = true
        AlertDialog.Builder(this).setTitle("Call ${f.first}?").setMessage(f.second)
            .setPositiveButton("Haan") { _, _ ->
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(f.second))))
                else say("Call ki permission allow karo.")
            }
            .setNegativeButton("Nahi", null)
            .setOnDismissListener { confirming = false; if (session && !busy) listen() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { sr?.destroy() } catch (e: Exception) { }
        tts.shutdown()
    }
}
