package com.project.argus

import android.app.Application
import com.project.argus.data.bluetooth.BluetoothCaneClient
import com.project.argus.data.tts.AndroidVoiceAlertService
import com.project.argus.domain.bluetooth.CaneBluetoothClient
import com.project.argus.domain.tts.VoiceAlertService
import com.project.argus.domain.usecase.EvaluateHazardsUseCase
import com.project.argus.domain.usecase.EvaluateSensorFusionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ArgusApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var voiceAlertService: VoiceAlertService
        private set

    lateinit var evaluateHazardsUseCase: EvaluateHazardsUseCase
        private set

    lateinit var evaluateSensorFusionUseCase: EvaluateSensorFusionUseCase
        private set

    lateinit var caneBluetoothClient: CaneBluetoothClient
        private set

    override fun onCreate() {
        super.onCreate()
        voiceAlertService = AndroidVoiceAlertService(this)
        evaluateHazardsUseCase = EvaluateHazardsUseCase()
        evaluateSensorFusionUseCase = EvaluateSensorFusionUseCase()
        caneBluetoothClient = BluetoothCaneClient(this, applicationScope)
    }

    override fun onTerminate() {
        super.onTerminate()
        caneBluetoothClient.disconnect()
        voiceAlertService.shutdown()
        applicationScope.cancel()
    }
}
