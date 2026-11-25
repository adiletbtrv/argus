package com.project.argus.data.haptic

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.project.argus.domain.haptic.HapticCooldownGate
import com.project.argus.domain.haptic.HapticFeedbackService

class AndroidHapticFeedbackService(
    context: Context,
    private val cooldownGate: HapticCooldownGate = HapticCooldownGate(
        cooldownMs = 1500L,
        clock = SystemClock::elapsedRealtime
    )
) : HapticFeedbackService {

    private val appContext: Context = context.applicationContext

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun vibrateUrgent() {
        val targetVibrator = vibrator
        if (targetVibrator == null || !targetVibrator.hasVibrator()) {
            return
        }
        if (!cooldownGate.shouldTrigger()) {
            return
        }
        runCatching {
            val effect = VibrationEffect.createWaveform(longArrayOf(0L, 120L, 80L, 180L), -1)
            targetVibrator.vibrate(effect)
        }
    }
}
