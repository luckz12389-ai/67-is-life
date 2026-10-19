package com.gamerecorder

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.*

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        @Volatile var isRecording = false
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var started = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!isRecording) start(intent)
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start(i: Intent) {
        val mic = i.getBooleanExtra("mic", true)
        val target = i.getIntExtra("res", 1080)
        val fps = i.getIntExtra("fps", 30)
        val code = i.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data = i.getParcelableExtra<Intent>("data") ?: return stopSelf()

        startForeground(1, buildNotification(), foregroundType(mic))
        isRecording = true

        // Tamanho proporcional à tela (sempre par)
        val m = resources.displayMetrics
        val scale = minOf(1f, target.toFloat() / minOf(m.widthPixels, m.heightPixels))
        val w = (m.widthPixels * scale).toInt() / 2 * 2
        val h = (m.heightPixels * scale).toInt() / 2 * 2
        val bitrate = if (target >= 1080) 12_000_000 else 6_000_000

        // Salva em Movies/GameRecorder via MediaStore
        val name = "Game_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GameRecorder")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        pfd = contentResolver.openFileDescriptor(uri!!, "w")

        recorder = MediaRecorder(this).apply {
            if (mic) setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (mic) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
            }
            setVideoSize(w, h)
            setVideoFrameRate(fps)
            setVideoEncodingBitRate(bitrate)
            setOutputFile(pfd!!.fileDescriptor)
            prepare()
        }

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data).apply {
            registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stop() }
            }, handler)
        }
        display = projection!!.createVirtualDisplay(
            "GameRecorder", w, h, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder!!.surface, null, null
        )

        // Contagem de 3s para o usuário abrir o jogo
        handler.postDelayed({
            if (isRecording) { recorder?.start(); started = true }
        }, 3000)
    }

    private fun stop() {
        if (!isRecording) return
        isRecording = false
        handler.removeCallbacksAndMessages(null)
        try { if (started) recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        display?.release(); display = null
        projection?.stop(); projection = null
        pfd?.close(); pfd = null
        uri?.let {
            val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(it, v, null, null)
        }
        started = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundType(mic: Boolean): Int {
        var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (mic) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        return t
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("rec", "Gravação", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "rec")
            .setContentTitle("Gravando a tela…")
            .setContentText("Toque em Parar para finalizar")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(Notification.Action.Builder(null, "Parar", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() { stop(); super.onDestroy() }
}
