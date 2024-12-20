package com.example.childtrackingappiot

import android.app.Application
import com.example.childtrackingappiot.data.api.RetrofitClient

class ChildTrackingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }
} 