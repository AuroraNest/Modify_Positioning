package com.aurora.modifypositioning

import android.app.Application
import com.amap.api.maps.MapsInitializer

class ModifyPositioningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 高德地图隐私合规初始化，未调用会导致地图无法正常显示。
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
    }
}
