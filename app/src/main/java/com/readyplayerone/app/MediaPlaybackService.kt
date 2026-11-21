package com.readyplayerone.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class MediaPlaybackService : Service() {
    private lateinit var session: MediaSessionCompat
    private var title = "READY PLAYER 1"
    private var artist = "Ready to play"
    private var album = "READY PLAYER 1"
    private var duration = 0L
    private var playing = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        session = MediaSessionCompat(this, "READY_PLAYER_1").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = sendCommand("play")
                override fun onPause() = sendCommand("pause")
                override fun onSkipToNext() = sendCommand("next")
                override fun onSkipToPrevious() = sendCommand("previous")
                override fun onStop() = sendCommand("stop")
            })
            isActive = true
        }
        updateSession()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                title = intent.getStringExtra(EXTRA_TITLE) ?: title
                artist = intent.getStringExtra(EXTRA_ARTIST) ?: artist
                album = intent.getStringExtra(EXTRA_ALBUM) ?: album
                duration = intent.getLongExtra(EXTRA_DURATION, duration)
                playing = intent.getBooleanExtra(EXTRA_PLAYING, playing)
                updateSession()
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification())
            }
            ACTION_INIT, null -> updateSession()
            "com.readyplayerone.COMMAND" -> sendCommand(intent.getStringExtra(EXTRA_COMMAND) ?: "pause")
        }
        return START_STICKY
    }

    private fun sendCommand(command: String) {
        if (command == "play") playing = true
        if (command == "pause" || command == "stop") playing = false
        updateSession()
        sendBroadcast(Intent(ACTION_WEB_COMMAND).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
    }

    private fun updateSession() {
        val art = try { assets.open("ready_player_1_art.jpg").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        if (art != null) metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art).putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
        session.setMetadata(metadata.build())
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f)
            .build())
    }

    private fun buildNotification(): Notification {
        val art = try { assets.open("ready_player_1_art.jpg").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prev = actionIntent("previous", 2)
        val playPause = actionIntent(if (playing) "pause" else "play", 3)
        val next = actionIntent("next", 4)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(artist).setSubText("READY PLAYER 1")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(art).setContentIntent(open).setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prev)
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play", playPause)
            .addAction(android.R.drawable.ic_media_next, "Next", next)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionIntent("stop", 5))
            .setStyle(MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun actionIntent(command: String, request: Int): PendingIntent = PendingIntent.getService(
        this, request, Intent(this, MediaPlaybackService::class.java).setAction("com.readyplayerone.COMMAND").putExtra(EXTRA_COMMAND, command),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "READY PLAYER 1 playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { session.isActive = false; session.release(); super.onDestroy() }

    companion object {
        const val ACTION_INIT = "com.readyplayerone.INIT"
        const val ACTION_UPDATE = "com.readyplayerone.UPDATE"
        const val ACTION_WEB_COMMAND = "com.readyplayerone.WEB_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_PLAYING = "playing"
        private const val CHANNEL_ID = "ready_player_playback"
        private const val NOTIFICATION_ID = 1201
    }
}
