package com.gamerecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var btn: Button
    private lateinit var mic: Switch
    private lateinit var res: RadioGroup
    private lateinit var fps: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#0F1115"))
        }
        fun label(t: String, size: Float = 16f) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.WHITE); setPadding(0, pad / 2, 0, pad / 4)
        }
        fun radios(vararg o: String) = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            o.forEachIndexed { i, s ->
                addView(RadioButton(context).apply { id = i + 1; text = s; setTextColor(Color.WHITE) })
            }
            check(1)
        }

        root.addView(label("🎮 Game Recorder", 28f))
        root.addView(label("Qualidade"))
        res = radios("720p", "1080p"); res.check(2); root.addView(res)
        root.addView(label("FPS"))
        fps = radios("30", "60"); root.addView(fps)
        mic = Switch(this).apply { text = "Gravar microfone"; isChecked = true; setTextColor(Color.WHITE) }
        root.addView(mic)

        btn = Button(this).apply {
            textSize = 18f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E53935"))
            setOnClickListener { onToggle() }
        }
        root.addView(btn, LinearLayout.LayoutParams(-1, pad * 3).apply { topMargin = pad * 2 })
        root.addView(label("Os vídeos são salvos em Movies/GameRecorder.").apply { gravity = Gravity.CENTER })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() { btn.text = if (RecorderService.isRecording) "■ PARAR" else "● GRAVAR" }

    private fun onToggle() {
        if (RecorderService.isRecording) {
            startService(Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
            btn.postDelayed({ refresh() }, 500)
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 10
            )
        }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        // Segue mesmo se o microfone for negado (grava sem áudio)
        if (code == 10) {
            if (mic.isChecked && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != 0) mic.isChecked = false
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(mgr.createScreenCaptureIntent(), 20)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, result: Int, data: Intent?) {
        super.onActivityResult(req, result, data)
        if (req == 20 && result == RESULT_OK && data != null) {
            val i = Intent(this, RecorderService::class.java)
                .setAction(RecorderService.ACTION_START)
                .putExtra("code", result)
                .putExtra("data", data)
                .putExtra("mic", mic.isChecked)
                .putExtra("res", if (res.checkedRadioButtonId == 1) 720 else 1080)
                .putExtra("fps", if (fps.checkedRadioButtonId == 1) 30 else 60)
            startForegroundService(i)
            RecorderService.isRecording = true
            moveTaskToBack(true) // vai para a tela inicial para você abrir o jogo
        }
    }
}
