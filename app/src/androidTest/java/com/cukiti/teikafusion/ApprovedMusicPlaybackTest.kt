package com.cukiti.teikafusion

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Android decoder/next-player checks, muted. Does not certify audible mix or gaplessness. */
@RunWith(AndroidJUnit4::class)
class ApprovedMusicPlaybackTest {
    @Test
    fun allInstalledMusicDecodesAndStartsItsPreparedSuccessor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = listOf(
            R.raw.bgm_gameplay, R.raw.bgm_crystal, R.raw.bgm_sleep, R.raw.bgm_fear,
            R.raw.bgm_garden, R.raw.bgm_storm, R.raw.bgm_volcano, R.raw.bgm_ninja,
            R.raw.bgm_glacier, R.raw.bgm_ghost, R.raw.bgm_glutton, R.raw.bgm_toxic,
            R.raw.bgm_star, R.raw.bgm_king, R.raw.bgm_galactic
        )
        val handler = Handler(Looper.getMainLooper())
        for (resource in resources) {
            val name = context.resources.getResourceEntryName(resource)
            val prepared = CountDownLatch(2)
            val completed = CountDownLatch(1)
            val checked = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            var first: MediaPlayer? = null
            var next: MediaPlayer? = null
            fun onMain(block: () -> Unit) {
                val done = CountDownLatch(1)
                handler.post {
                    try { block() } catch (e: Exception) { error.set(e.toString()) }
                    finally { done.countDown() }
                }
                assertTrue("$name main callback timeout", done.await(10, TimeUnit.SECONDS))
            }
            try {
                onMain {
                    fun player(): MediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        isLooping = false
                        setVolume(0f, 0f)
                        setOnErrorListener { _, what, extra ->
                            error.set("MediaPlayer error $what/$extra")
                            prepared.countDown(); completed.countDown(); true
                        }
                        setOnPreparedListener { prepared.countDown() }
                        setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resource"))
                        prepareAsync()
                    }
                    first = player()
                    next = player()
                }
                assertTrue("$name prepare timeout", prepared.await(15, TimeUnit.SECONDS))
                assertTrue("$name: ${error.get()}", error.get() == null)
                onMain {
                    val current = first!!
                    val successor = next!!
                    check(current.duration in 110_000..165_000) { "Unexpected full duration ${current.duration}" }
                    current.setNextMediaPlayer(successor)
                    current.setOnCompletionListener { completed.countDown() }
                    current.setOnSeekCompleteListener { current.start() }
                    current.seekTo(current.duration - 1200)
                }
                assertTrue("$name completion timeout", completed.await(8, TimeUnit.SECONDS))
                // currentPosition is a cached Android decoder query, not a measure of audible latency.
                val deadline = SystemClock.elapsedRealtime() + 3000
                handler.post(object : Runnable {
                    override fun run() {
                        try {
                            val successor = next!!
                            if ((!successor.isPlaying || successor.currentPosition == 0) &&
                                SystemClock.elapsedRealtime() < deadline) {
                                handler.postDelayed(this, 100)
                                return
                            }
                            check(successor.isPlaying && successor.currentPosition > 0) {
                                "Prepared successor did not advance: playing=${successor.isPlaying}, position=${successor.currentPosition}, duration=${successor.duration}"
                            }
                            successor.pause()
                            check(!successor.isPlaying) { "Pause failed" }
                            successor.start()
                            check(successor.isPlaying) { "Resume failed" }
                        } catch (e: Exception) { error.set(e.toString()) }
                        checked.countDown()
                    }
                })
                assertTrue("$name successor check timeout", checked.await(5, TimeUnit.SECONDS))
                assertTrue("$name: ${error.get()}", error.get() == null)
            } finally {
                onMain {
                    first?.setNextMediaPlayer(null)
                    first?.release()
                    next?.release()
                }
            }
        }
    }
}
