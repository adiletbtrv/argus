package com.project.argus

import android.app.Application
import com.project.argus.data.tts.AndroidVoiceAlertService
import com.project.argus.domain.tts.VoiceAlertService
import com.project.argus.domain.usecase.EvaluateHazardsUseCase


class ArgusApplication : Application() {

    lateinit var voiceAlertService: VoiceAlertService
        private set

    lateinit var evaluateHazardsUseCase: EvaluateHazardsUseCase
        private set

    override fun onCreate() {
        super.onCreate()
        voiceAlertService = AndroidVoiceAlertService(this)
        evaluateHazardsUseCase = EvaluateHazardsUseCase()
    }

    override fun onTerminate() {
        super.onTerminate()
        voiceAlertService.shutdown()
    }
}
