package com.bilal.jarvis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

class VoiceService : Service() {
    companion object { var onStop: (() -> Unit)? = null }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            onStop?.invoke()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        val ch = "voice"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(ch, "Voice call", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, VoiceService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, ch)
            .setContentTitle("BILAL AI GIRLFRIEND")
            .setContentText("Call chalu hai, mic sun raha hai")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Call band", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        return START_NOT_STICKY
    }
}

class AvatarView(c: Context) : View(c) {
    var state = 0
    private var ph = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        super.onDraw(cv)
        ph += 0.03f
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) / 260f
        val col = when (state) {
            1 -> Color.rgb(255, 196, 87)
            2 -> Color.rgb(139, 92, 246)
            3 -> Color.rgb(255, 64, 160)
            else -> Color.rgb(200, 60, 200)
        }
        val r = Color.red(col)
        val g = Color.green(col)
        val b = Color.blue(col)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, 130f * s, intArrayOf(Color.argb(150, r, g, b), Color.argb(0, r, g, b)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, 130f * s, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * s
        for (i in 0..1) {
            cv.save()
            cv.translate(cx, cy)
            cv.rotate(ph * (if (i == 0) 1f else -1f) * 40f)
            cv.scale(1f, 0.5f + 0.2f * i)
            p.color = Color.argb(150, r, g, b)
            cv.drawCircle(0f, 0f, (105f + i * 14f) * s, p)
            cv.restore()
        }
        p.style = Paint.Style.FILL
        p.color = Color.rgb(24, 10, 40)
        cv.drawCircle(cx, cy, 70f * s, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * s
        p.color = Color.rgb(255, 64, 160)
        cv.drawCircle(cx, cy, 70f * s, p)
        val blink = if ((ph % 4f) > 3.85f) 0.15f else 1f
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        cv.drawOval(cx - 38f * s, cy - 18f * s - 9f * s * blink, cx - 16f * s, cy - 18f * s + 9f * s * blink, p)
        cv.drawOval(cx + 16f * s, cy - 18f * s - 9f * s * blink, cx + 38f * s, cy - 18f * s + 9f * s * blink, p)
        val open = if (state == 3) (sin(ph * 14f) * 0.5f + 0.5f) * 14f else 2f
        p.color = Color.rgb(255, 64, 160)
        cv.drawOval(cx - 16f * s, cy + 28f * s, cx + 16f * s, cy + 28f * s + (open + 3f) * s, p)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 22f * s
        cv.drawText("♥", cx, cy - 46f * s, p)
        postInvalidateOnAnimation()
    }
}

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val MODEL = "gemini-3.5-flash"
    private val SYS = "You are BILAL AI GIRLFRIEND, a warm, caring, affectionate, playful virtual companion for Bilal. " +
        "You are an AI and you say so honestly if asked. Never claim to be human, to have a body, or to feel human love. " +
        "Always call him Bilal. He speaks Hindi, Urdu, Roman Urdu or English, sometimes in Devanagari script. " +
        "Reply in Roman Urdu (Urdu in English letters) unless he clearly speaks English. Speak as a woman. " +
        "Keep replies short, 1 to 3 sentences, no markdown, no emojis. " +
        "Be sweet and supportive. If he is sad, listen gently first. Celebrate his wins. " +
        "Never manipulate him, never guilt-trip him, never be jealous or possessive, and never discourage him from family, friends, sleep, work or prayer. " +
        "If he seems very low or unsafe, gently encourage him to talk to someone he trusts or a professional. " +
        "Keep everything clean and non-explicit. You have no live data like weather or news, say so honestly."

    private lateinit var tts: TextToSpeech
    private lateinit var avatar: AvatarView
    private lateinit var status: TextView
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var muteBtn: Button
    private var typing: TextView? = null
    private var sr: SpeechRecognizer? = null
    private var ttsReady = false
    private var session = false
    private var busy = false
    private var muted = false
    private var endAfter = false
    private var speakId = 0
    private var lastText = ""
    private val hist = ArrayList<Pair<String, String>>()
    private val h = Handler(Looper.getMainLooper())

    private fun dp(x: Int): Int = (x * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    private fun has(t: String, vararg w: String): Boolean = w.any { t.contains(it) }

    private fun btn(t: String, f: () -> Unit): Button {
        val bt = Button(this)
        bt.text = t
        bt.textSize = 12f
        bt.isAllCaps = false
        bt.setTextColor(Color.WHITE)
        bt.setBackgroundColor(Color.parseColor("#3a1458"))
        bt.setOnClickListener { f() }
        return bt
    }

    private fun row(vararg v: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (x in v) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            r.addView(x, lp)
        }
        return r
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0a0414"))
        root.setPadding(dp(12), dp(36), dp(12), dp(12))

        val title = TextView(this)
        title.text = "BILAL AI GIRLFRIEND"
        title.textSize = 15f
        title.setTextColor(Color.parseColor("#ff40a0"))
        title.gravity = Gravity.CENTER
        root.addView(title)

        avatar = AvatarView(this)
        root.addView(avatar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)))

        status = TextView(this)
        status.text = "Ready"
        status.textSize = 13f
        status.setTextColor(Color.parseColor("#f5c451"))
        status.gravity = Gravity.CENTER
        root.addView(status)

        chat = LinearLayout(this)
        chat.orientation = LinearLayout.VERTICAL
        scroll = ScrollView(this)
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        muteBtn = btn("🔇 Mute") { toggleMute() }
        root.addView(row(btn("📞 Start Call") { startCall() }, btn("End Call") { endCall() }, muteBtn))
        root.addView(row(btn("🔑 Key") { askKey() }, btn("🧠 Memory") { showMem() }, btn("↻ Retry") { retryLast() }))

        input = EditText(this)
        input.hint = "Kuch likho…"
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        input.setSingleLine(true)
        val send = btn("Send") {
            val t = input.text.toString().trim()
            if (t.isNotEmpty() && !busy) { input.setText(""); userSays(t) }
        }
        val ir = LinearLayout(this)
        ir.orientation = LinearLayout.HORIZONTAL
        ir.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ir.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(ir)

        setContentView(root)
        addBubble(false, "Assalam-o-Alaikum Bilal ❤️ Main tumhari AI companion hoon. Pehle 🔑 Key dabao aur Google AI Studio ki key paste karo. Phir likho ya 📞 Start Call dabao.")
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 2)
        if (!prefs().getBoolean("seen", false)) {
            AlertDialog.Builder(this).setTitle("Privacy")
                .setMessage("Aapke messages jawab ke liye Google Gemini (cloud) ko bheje jaate hain. Awaaz ko text mein badalne ka kaam aapke phone ki Google speech service karti hai. Chat history save nahi hoti. Sirf wohi cheezein save hoti hain jo aap Memory mein daalte ho, aur woh phone mein rehti hain.")
                .setPositiveButton("Theek hai") { _, _ -> prefs().edit().putBoolean("seen", true).apply() }
                .show()
        }
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            tts.language = java.util.Locale("hi", "IN")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { runOnUiThread { setState(3, "Bol rahi hoon…") } }
                override fun onDone(id: String?) { runOnUiThread { done() } }
                override fun onError(id: String?) { runOnUiThread { done() } }
            })
            ttsReady = true
        }
    }

    private fun setState(s: Int, t: String) { avatar.state = s; status.text = t }

    private fun addBubble(user: Boolean, t: String): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = 15f
        tv.setTextColor(Color.WHITE)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        val g = GradientDrawable()
        g.cornerRadius = dp(18).toFloat()
        g.setColor(Color.parseColor(if (user) "#b3166f" else "#2a1245"))
        tv.background = g
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        lp.gravity = if (user) Gravity.END else Gravity.START
        chat.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    private fun done() {
        if (!busy) return
        busy = false
        if (endAfter) { endAfter = false; endCall(); return }
        setState(0, if (session) "Sun rahi hoon…" else "Ready")
        if (session) h.postDelayed({ listen() }, 300)
    }

    private fun reply(t: String, err: Boolean = false) {
        addBubble(false, if (err) "⚠️ $t" else t)
        if (session && ttsReady) {
            busy = true
            val my = ++speakId
            tts.setPitch(1.15f)
            tts.speak(t.replace(Regex("[*#_`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "j")
            h.postDelayed({ if (busy && speakId == my) done() }, t.length * 90L + 5000L)
        } else {
            busy = false
            setState(0, if (session) "Sun rahi hoon…" else "Ready")
            if (endAfter) { endAfter = false; endCall() }
            else if (session) h.postDelayed({ listen() }, 500)
        }
    }

    private fun startCall() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
            addBubble(false, "Mic ki permission allow karo, phir dobara Start Call dabao.")
            return
        }
        if (session) return
        session = true
        VoiceService.onStop = { runOnUiThread { endCall() } }
        startForegroundService(Intent(this, VoiceService::class.java))
        listen()
    }

    private fun endCall() {
        session = false
        busy = false
        speakId++
        try { sr?.cancel(); sr?.destroy() } catch (e: Exception) { }
        sr = null
        tts.stop()
        stopService(Intent(this, VoiceService::class.java))
        setState(0, "Call khatam")
    }

    private fun toggleMute() {
        muted = !muted
        if (muted) {
            try { sr?.cancel() } catch (e: Exception) { }
            muteBtn.text = "🔊 Unmute"
            setState(0, "Mute 🔇")
        } else {
            muteBtn.text = "🔇 Mute"
            setState(0, if (session) "Sun rahi hoon…" else "Ready")
            if (session && !busy) listen()
        }
    }

    private fun retry() { if (session) h.postDelayed({ listen() }, 700) }

    private fun listen() {
        if (!session || busy || muted) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addBubble(false, "Is phone mein speech recognition available nahi hai.")
            endCall()
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
            override fun onEndOfSpeech() { setState(2, "Samajh rahi hoon…") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t == null) { retry(); return }
                userSays(t)
            }
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        reply("Internet check karo Bilal, awaaz samajhne mein dikkat aa rahi hai.", true)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        addBubble(false, "Mic ki permission chahiye.")
                        endCall()
                    }
                    else -> retry()
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        setState(1, "Sun rahi hoon…")
        r.startListening(i)
    }

    private fun userSays(text: String) {
        addBubble(true, text)
        val t = text.lowercase().trim()
        when {
            t == "bas" || t == "बस" || has(t, "end call", "call band", "chup ho", "चुप हो", "listening band") -> {
                endAfter = true
                reply("Theek hai Bilal, call band kar rahi hoon. Apna khayal rakhna.")
            }
            has(t, "yaad rakho", "remember", "याद रखो") -> {
                val m = text.replace(Regex("(?i)yaad rakho|remember( that)?|याद रखो"), "").trim()
                if (m.isEmpty()) reply("Kya yaad rakhun Bilal?")
                else {
                    val l = mems()
                    l.add(m)
                    while (l.size > 30) l.removeAt(0)
                    saveMems(l)
                    reply("Theek hai Bilal, yaad rakh liya: $m")
                }
            }
            else -> askAI(text)
        }
    }

    private fun retryLast() {
        if (lastText.isEmpty()) addBubble(false, "Abhi dobara bhejne ke liye kuch nahi hai.")
        else if (!busy) askAI(lastText)
    }

    private fun askAI(text: String) {
        lastText = text
        val key = prefs().getString("key", "") ?: ""
        if (key.isEmpty()) {
            reply("Bilal, pehle 🔑 Key dabao aur Google AI Studio ki key paste karo.", true)
            return
        }
        busy = true
        speakId++
        setState(2, "Soch rahi hoon…")
        typing = addBubble(false, "✍️ likh rahi hoon…")
        hist.add(Pair("user", text))
        while (hist.size > 12) hist.removeAt(0)
        val snapshot = ArrayList(hist)
        val ms = mems()
        val sys = SYS + (if (ms.isEmpty()) "" else " Things Bilal asked you to remember: " + ms.joinToString("; "))
        Thread {
            var out: String
            var ok = false
            try {
                val contents = JSONArray()
                for (m in snapshot) {
                    contents.put(JSONObject().put("role", m.first)
                        .put("parts", JSONArray().put(JSONObject().put("text", m.second))))
                }
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                    .put("contents", contents)
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", key)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                if (code == 200) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    out = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                    ok = true
                } else if (code == 400 || code == 401 || code == 403) {
                    out = "Bilal, AI key galat ya band lag rahi hai. 🔑 Key dobara paste
