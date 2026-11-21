package com.readyplayerone.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Activity
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(MediaPlaybackService.EXTRA_COMMAND) ?: return
            val js = when (command) {
                "play" -> "window.rp1NativeCommand && window.rp1NativeCommand('play')"
                "pause" -> "window.rp1NativeCommand && window.rp1NativeCommand('pause')"
                "next" -> "window.rp1NativeCommand && window.rp1NativeCommand('next')"
                "previous" -> "window.rp1NativeCommand && window.rp1NativeCommand('previous')"
                "stop" -> "window.rp1NativeCommand && window.rp1NativeCommand('stop')"
                else -> ""
            }
            if (js.isNotEmpty()) web.evaluateJavascript(js, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(this, Intent(this, MediaPlaybackService::class.java).setAction(MediaPlaybackService.ACTION_INIT))
        web = WebView(this)
        web.setBackgroundColor(0xFF040508.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.addJavascriptInterface(PlayerBridge(this), "ReadyPlayerNative")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(intent, FILE_PICKER_REQUEST)
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                installMediaHooks()
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        val filter = IntentFilter(MediaPlaybackService.ACTION_WEB_COMMAND)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mediaReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(mediaReceiver, filter)
        }
    }

    private fun installMediaHooks() {
        val js = """
        (function(){
          if(window.__rp1HooksInstalled) return; window.__rp1HooksInstalled=true;
          const art='file:///android_asset/ready_player_1_art.jpg';
          let lastTitle='READY PLAYER 1', lastArtist='Now Playing';
          function send(el, playing){
            try {
              const title=(el && (el.getAttribute('data-title') || el.getAttribute('title'))) || document.title || 'READY PLAYER 1';
              const artist=(el && (el.getAttribute('data-artist'))) || 'READY PLAYER 1';
              lastTitle=title; lastArtist=artist;
              const duration=el && isFinite(el.duration) ? Math.floor(el.duration*1000) : 0;
              ReadyPlayerNative.updateMetadata(title,artist,'READY PLAYER 1',duration,!!playing);
            } catch(e){}
          }
          function hook(el){ if(el.__rp1Hooked) return; el.__rp1Hooked=true;
            el.addEventListener('play',()=>send(el,true));
            el.addEventListener('playing',()=>send(el,true));
            el.addEventListener('pause',()=>send(el,false));
            el.addEventListener('ended',()=>send(el,false));
            el.addEventListener('loadedmetadata',()=>send(el,!el.paused));
          }
          document.querySelectorAll('audio,video').forEach(hook);
          new MutationObserver(()=>document.querySelectorAll('audio,video').forEach(hook)).observe(document.documentElement,{childList:true,subtree:true});
          window.rp1NativeCommand=function(cmd){
            const media=[...document.querySelectorAll('audio,video')]; const el=media.find(x=>!x.paused) || media[0];
            if(cmd==='play'){ if(el){el.play().catch(()=>{});} else clickBy(/play|resume|start/i); }
            if(cmd==='pause'){ if(el) el.pause(); else clickBy(/pause/i); }
            if(cmd==='stop'){ if(el){el.pause(); try{el.currentTime=0}catch(e){}} else clickBy(/stop/i); }
            if(cmd==='next') clickBy(/next|skip forward/i);
            if(cmd==='previous') clickBy(/previous|prev|skip back/i);
            function clickBy(re){ const nodes=[...document.querySelectorAll('button,[role=button]')]; const b=nodes.find(x=>re.test((x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||'')+' '+(x.innerText||''))); if(b)b.click(); }
          };
        })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST) {
            val uris = if (resultCode == Activity.RESULT_OK && data != null) {
                val clip = data.clipData
                if (clip != null) Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                else data.data?.let { arrayOf(it) }
            } else null
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(mediaReceiver) } catch (_: Exception) {}
        web.destroy()
        super.onDestroy()
    }

    inner class PlayerBridge(private val ctx: Context) {
        @JavascriptInterface fun updateMetadata(title: String?, artist: String?, album: String?, durationMs: Long, playing: Boolean) {
            val i = Intent(ctx, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_UPDATE
                putExtra(MediaPlaybackService.EXTRA_TITLE, title ?: "READY PLAYER 1")
                putExtra(MediaPlaybackService.EXTRA_ARTIST, artist ?: "READY PLAYER 1")
                putExtra(MediaPlaybackService.EXTRA_ALBUM, album ?: "READY PLAYER 1")
                putExtra(MediaPlaybackService.EXTRA_DURATION, durationMs)
                putExtra(MediaPlaybackService.EXTRA_PLAYING, playing)
            }
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    companion object { private const val FILE_PICKER_REQUEST = 4102 }
}
