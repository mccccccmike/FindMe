package com.example.myapplication

import android.app.Application
import com.facebook.stetho.Stetho
import com.parse.Parse;

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Stetho.initializeWithDefaults(this)
        Parse.initialize(Parse.Configuration.Builder(this)
            .applicationId(getString(R.string.back4app_app_id))
            .clientKey(getString(R.string.back4app_client_key))
            .server(getString(R.string.back4app_server_url))
            .build())
    }
}