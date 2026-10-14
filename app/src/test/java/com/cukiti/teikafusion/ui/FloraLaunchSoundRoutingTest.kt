package com.cukiti.teikafusion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prevent the game-start bell from being reused as the cannon's successful launch cue. */
class FloraLaunchSoundRoutingTest {
    @Test
    fun floraLaunchUsesItsOwnCueWhileGameStartKeepsTheBell() {
        var root = File("").absoluteFile
        while (!File(root, "app/src/main/java/com/cukiti/teikafusion/ui/SoundManager.kt").isFile) {
            root = requireNotNull(root.parentFile)
        }
        val source = File(root, "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt").readText()
        val flora = source.substringAfter("fun launchFloraTeika(").substringBefore("/** Madera PLANA")
        assertTrue(flora.contains("soundManager.playFloraLaunch()"))
        assertFalse("Flora must not announce another match", flora.contains("soundManager.playLaunch()"))
        val mixer = File(root, "app/src/main/java/com/cukiti/teikafusion/ui/SoundManager.kt").readText()
        assertTrue(mixer.contains("fun playFloraLaunch() = play(floraLaunchId)"))
        assertTrue(mixer.contains("fun playLaunch() = play(launchId)"))
        assertTrue(mixer.contains("R.raw.snd_flora_launch"))
        assertTrue(mixer.contains("R.raw.snd_play_launch"))
    }
}
