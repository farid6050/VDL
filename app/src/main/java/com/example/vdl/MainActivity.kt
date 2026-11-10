package com.example.vdl

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class MainActivity : Activity() {

    private lateinit var urlBox: EditText
    private lateinit var bar: ProgressBar
    private lateinit var status: TextView
    private lateinit var btnVideo: Button
    private lateinit var btnAudio: Button
    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
        }
        urlBox = EditText(this).apply { hint = "ভিডিও লিংক এখানে পেস্ট করো" }
        btnVideo = Button(this).apply { text = "ভিডিও ডাউনলোড" }
        btnAudio = Button(this).apply { text = "MP3 ডাউনলোড" }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        status = TextView(this).apply { text = "প্রস্তুত হচ্ছে..." }

        root.addView(urlBox)
        root.addView(btnVideo)
        root.addView(btnAudio)
        root.addView(bar)
        root.addView(ScrollView(this).apply { addView(status) })
        setContentView(root)

        btnVideo.setOnClickListener { start(false) }
        btnAudio.setOnClickListener { start(true) }

        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                say("তৈরি। লিংক পেস্ট করো, অথবা অন্য অ্যাপ থেকে Share করে এই অ্যাপ বেছে নাও।")
            } catch (e: Exception) {
                say("শুরু করতে সমস্যা: ${e.message}")
            }
        }.start()

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        urlBox.setText(Regex("https?://\\S+").find(text)?.value ?: text)
    }

    private fun say(s: String) = runOnUiThread { status.text = s }

    private fun lock(on: Boolean) = runOnUiThread {
        btnVideo.isEnabled = !on
        btnAudio.isEnabled = !on
    }

    private fun start(audio: Boolean) {
        val url = urlBox.text.toString().trim()
        if (!ready) { say("একটু অপেক্ষা করো, প্রস্তুত হচ্ছে..."); return }
        if (url.isEmpty()) { say("আগে লিংক দাও"); return }
        lock(true)
        bar.progress = 0
        say("শুরু হচ্ছে...")

        Thread {
            val tmp = File(getExternalFilesDir(null), "t" + System.currentTimeMillis()).apply { mkdirs() }
            try {
                val req = YoutubeDLRequest(url)
                req.addOption("-o", tmp.absolutePath + "/%(title).80s.%(ext)s")
                req.addOption("--no-playlist")
                if (audio) {
                    req.addOption("-x")
                    req.addOption("--audio-format", "mp3")
                } else {
                    req.addOption("-f", "b[ext=mp4]/b")
                }
                YoutubeDL.getInstance().execute(req, null) { p, _, _ ->
                    runOnUiThread { bar.progress = p.toInt().coerceAtLeast(0) }
                }
                var saved = 0
                tmp.listFiles()?.forEach {
                    if (!it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && save(it)) saved++
                }
                say(if (saved > 0) "ডাউনলোড শেষ ✓  (Downloads/VDL ফোল্ডারে)" else "ফাইল পাওয়া যায়নি")
            } catch (e: Exception) {
                say("ব্যর্থ: ${e.message}")
            } finally {
                tmp.deleteRecursively()
                lock(false)
            }
        }.start()
    }

    private fun save(f: File): Boolean {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension)
            ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VDL")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        val out = contentResolver.openOutputStream(uri) ?: return false
        out.use { o -> f.inputStream().use { it.copyTo(o) } }
        return true
    }
}
