package com.cukiti.teikafusion

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real device codec/load check; playback muted, so this does not certify the audible mix. */
@RunWith(AndroidJUnit4::class)
class ApprovedPlaceSfxPlaybackTest {
    @Test
    fun approvedPlaceWavLoadsAndPlaysAtAllSharedCueRates() {
        checkResource(R.raw.snd_place_classic, listOf(0.8f, 0.85f, 1f, 1.1f, 1.2f))
    }

    @Test
    fun approvedCoreWavsLoadAndPlayAtUiClearAndMergeRates() {
        checkResource(R.raw.snd_ui_click, listOf(0.9f, 0.92f, 1f, 1.45f, 1.9f))
        checkResource(R.raw.snd_clear_classic, listOf(1f, 1.26f, 1.5f, 2f))
        checkResource(R.raw.snd_merge_2, listOf(1.15f, 1f, 0.87f, 0.76f))
    }

    @Test
    fun approvedCrystalAndWoodWavsLoadAndPlayAtSharedRates() {
        checkResource(R.raw.snd_place_crystal, listOf(0.8f, 0.85f, 1f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f))
        checkResource(R.raw.snd_clear_crystal, listOf(0.75f, 0.8f, 1f, 1.2f, 1.26f, 1.3f, 1.35f, 1.4f, 1.5f, 2f))
        checkResource(R.raw.snd_place_wood, listOf(0.85f, 1f, 1.2f))
        checkResource(R.raw.snd_clear_wood, listOf(1f, 1.26f, 1.5f, 2f))
    }

    @Test
    fun approvedLavaAndIceWavsLoadAndPlayAtCueRates() {
        checkResource(R.raw.snd_place_lava, listOf(1f))
        checkResource(R.raw.snd_clear_lava, listOf(1f, 1.26f, 1.5f, 2f))
        checkResource(R.raw.snd_place_ice, listOf(1f))
        checkResource(R.raw.snd_clear_ice, listOf(1f, 1.26f, 1.5f, 2f))
    }

    @Test
    fun approvedSpectreAndConstellationWavsLoadAndPlayAtCueRates() {
        checkResource(R.raw.snd_place_spectre, listOf(1f))
        checkResource(R.raw.snd_clear_spectre, listOf(1f, 1.26f, 1.5f, 2f))
        checkResource(R.raw.snd_place_constellation, listOf(1f))
        checkResource(R.raw.snd_clear_constellation, listOf(1f, 1.26f, 1.5f, 2f))
    }

    @Test
    fun approvedVoidWavsLoadAndPlayAtLayerAndRuleRates() {
        checkResource(R.raw.snd_place_void, listOf(1f))
        checkResource(R.raw.snd_clear_void, listOf(0.8f, 1f, 1.1f, 1.15f, 1.25f, 1.26f, 1.5f, 2f))
    }

    @Test
    fun approvedMetalWavsLoadAndPlayAtLayerAndRuleRates() {
        checkResource(R.raw.snd_place_metal, listOf(0.7f, 0.72f, 0.8f, 1f, 1.25f))
        checkResource(R.raw.snd_clear_metal, listOf(1f, 1.26f, 1.5f, 2f))
    }

    @Test
    fun approvedGoldAndCeramicClearWavsLoadAndPlayAtCueRates() {
        checkResource(R.raw.snd_place_gold, listOf(1f))
        checkResource(R.raw.snd_clear_gold, listOf(1f, 1.26f, 1.5f, 2f))
        checkResource(R.raw.snd_clear_ceramic, listOf(1f, 1.26f, 1.5f, 2f))
    }

    @Test
    fun approvedNeonClearAndCeramicPlaceWavsLoadAndPlayAtCueRates() {
        checkResource(R.raw.snd_clear_neon, listOf(1f, 1.26f, 1.5f, 2f))
        checkResource(R.raw.snd_place_ceramic, listOf(1f))
    }

    @Test
    fun approvedInvalidWavLoadsAndPlaysAtUiRate() {
        checkResource(R.raw.snd_invalid, listOf(1f))
    }

    @Test
    fun approvedStartBellAndFloraWavsLoadAndPlayAtCueRates() {
        checkResource(R.raw.snd_play_launch, listOf(1f))
        checkResource(R.raw.snd_flora_launch, listOf(1f))
    }

    @Test
    fun approvedIrisCloseAndOpenWavsLoadAndPlayAtCueRates() {
        checkResource(R.raw.snd_iris_transition, listOf(1f))
        checkResource(R.raw.snd_iris_open, listOf(1f))
    }

    @Test
    fun approvedComboAndBoardWavsLoadAndPlayAtCueRates() {
        checkResource(R.raw.snd_combo, listOf(1f, 1.085f, 1.17f, 1.34f, 1.68f, 2f))
        checkResource(R.raw.snd_combo_grace, listOf(1f))
        checkResource(R.raw.snd_pure_line, listOf(1f))
        checkResource(R.raw.snd_perfect_clear, listOf(1f))
    }

    @Test
    fun approvedStarAndTeikaWavsLoadAndPlayAtSharedCueRates() {
        checkResource(R.raw.snd_target_collect, listOf(1f))
        checkResource(R.raw.snd_teika_appear, listOf(0.9f, 1f, 1.2f))
        checkResource(R.raw.snd_teika_seed_break, listOf(1f))
    }

    @Test
    fun approvedTrayRewardWavLoadsAndPlaysAtSharedCueRate() {
        checkResource(R.raw.snd_tray_reward, listOf(1f))
    }

    @Test
    fun approvedMegaExpansionWavLoadsAndPlaysAtCueRate() {
        checkResource(R.raw.snd_mega, listOf(1f))
    }

    @Test
    fun approvedMegaChargeWavLoadsAndPlaysAtCueRate() {
        checkResource(R.raw.snd_mega_collapse, listOf(1f))
    }

    @Test
    fun approvedMoveAndEchoWavsLoadAndPlayAtSharedCueRate() {
        checkResource(R.raw.snd_board_shift, listOf(1f))
        checkResource(R.raw.snd_ability_ready, listOf(1f))
    }

    @Test
    fun approvedTimoteoAndFloraWavsLoadAndPlayAtCueRate() {
        checkResource(R.raw.snd_ability_slide, listOf(1f))
        checkResource(R.raw.snd_ability_bloom, listOf(1f))
    }

    @Test
    fun approvedCristobalAndMaiteWavsLoadAndPlayAtCueRate() {
        checkResource(R.raw.snd_ability_shield, listOf(1f))
        checkResource(R.raw.snd_ability_dream, listOf(1f))
    }

    @Test
    fun approvedRaycoInakiKenjiAndNievesWavsLoadAndPlayAtCueRate() {
        checkResource(R.raw.snd_ability_energize, listOf(1f))
        checkResource(R.raw.snd_ability_fire, listOf(1f))
        checkResource(R.raw.snd_ability_slash, listOf(1f))
        checkResource(R.raw.snd_ability_icicle, listOf(1f))
    }

    private fun checkResource(resource: Int, rates: List<Float>) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loaded = CountDownLatch(1)
        val status = AtomicInteger(-1)
        val loadedId = AtomicInteger(0)
        val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        try {
            pool.setOnLoadCompleteListener { _, sampleId, result ->
                loadedId.set(sampleId)
                status.set(result)
                loaded.countDown()
            }
            val sampleId = pool.load(context, resource, 1)
            assertTrue("SoundPool rejected the resource", sampleId > 0)
            assertTrue("WAV load timeout", loaded.await(15, TimeUnit.SECONDS))
            assertEquals("WAV codec/load failed", 0, status.get())
            assertEquals(sampleId, loadedId.get())
            for (rate in rates) {
                val streamId = pool.play(sampleId, 0f, 0f, 1, 0, rate)
                assertTrue("Could not start approved placement at rate $rate", streamId > 0)
                pool.stop(streamId)
            }
        } finally {
            pool.release()
        }
    }
}
