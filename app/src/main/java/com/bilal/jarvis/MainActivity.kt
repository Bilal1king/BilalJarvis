package com.bilal.jarvis

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var log: TextView
    private val REQ_SPEECH = 1
    private val REQ_PERM = 2

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#070b1a"))
            setPadding(40, 80, 40, 40); gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply { text = "BILAL JARVIS"; textSize = 26f; setTextColor(Color.CYAN); gravity = Gravity.CENTER }
        log = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE); setPadding(0, 40, 0, 40); text = "Assalam-o-Alaikum Bilal. Neeche button dabao aur bolo.\n\nMisaal: \"Abu ko call karo\", \"speaker on\", \"battery\", \"YouTube kholo\"" }
        val talk = Button(this).apply { text = "🎙 Bolo"; textSize = 22f; setOnClickListener { listen() } }
        val spOn = Button(this).apply { text = "Speaker ON"; setOnClickListener { speaker(true) } }
        val spOff = Button(this).apply { text = "Speaker OFF"; setOnClickListener { speaker(false) } }
        root.addView(title); root.addView(log); root.addView(talk); root.addView(spOn); root.addView(spOff)
        setContentView(root)
        requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), REQ_PERM)
    }

    override fun onInit(s: Int) { if (s == TextToSpeech.SUCCESS) tts.language = Locale("hi", "IN") }

    private fun say(t: String) { log.append("\n\nJarvis: $t"); tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j") }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        try { startActivityForResult(i, REQ_SPEECH) } catch (e: Exception) { say("Is phone mein speech recognition available nahi hai.") }
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == REQ_SPEECH && res == RESULT_OK) {
            val t = d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            log.append("\n\nBilal: $t"); handle(t.lowercase())
        }
    }

    private fun handle(t: String) {
        when {
            "battery" in t -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                say("Bilal, battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent hai" + if (bm.isCharging) " aur charge ho rahi hai." else ".")
            }
            "time" in t || "waqt" in t || "baje" in t -> say("Abhi " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) + " ho rahe hain.")
            "youtube" in t -> {
                val i = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (i != null) { startActivity(i); say("YouTube khol raha hoon.") }
                else { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))); say("YouTube app nahi mili, browser mein khol diya.") }
            }
            "speaker" in t -> speaker("on" in t || "chalu" in t || "lagao" in t)
            "call" in t -> callByName(t)
            else -> say("Bilal, yeh command abhi mujhe samajh nahi aayi. Battery, time, YouTube, speaker ya call try karo.")
        }
    }

    private fun speaker(on: Boolean) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = on
        say("Speaker " + (if (on) "on" else "off") + " kar diya. Note: Android kabhi kabhi call ke dauran third-party app ko speaker control karne nahi deta, to call screen ka speaker button bhi check karna.")
    }

    private fun callByName(t: String) {
        val name = t.replace(Regex("\\b(jarvis|call|karo|lagao|ko|kijiye|please|ab|mujhe|mere)\\b"), "").trim()
        if (name.isEmpty()) { say("Kisko call karni hai Bilal?"); return }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { say("Contacts ki permission chahiye. Settings mein allow kar do."); return }
        var found: Pair<String, String>? = null
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)?.use {
            if (it.moveToFirst()) found = Pair(it.getString(0), it.getString(1))
        }
        val f = found
        if (f == null) { say("Bilal, contacts mein \"$name\" nahi mila."); return }
        say("${f.first} ke number par call lagane wala hoon. Kya call kar doon?")
        AlertDialog.Builder(this).setTitle("Call ${f.first}?").setMessage(f.second)
            .setPositiveButton("Haan") { _, _ ->
                if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(f.second))))
                else say("Call ki permission allow karo.")
            }.setNegativeButton("Nahi", null).show()
    }
}
