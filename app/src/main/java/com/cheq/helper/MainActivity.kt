package com.cheq.helper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.openPaytm).setOnClickListener {
            val launch = packageManager.getLaunchIntentForPackage("net.one97.paytm")
            if (launch != null) {
                startActivity(launch)
            } else {
                findViewById<TextView>(R.id.result).text = "Paytm app not found."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("bill", MODE_PRIVATE)
        findViewById<TextView>(R.id.result).text =
            prefs.getString("result", "Visible bill result will appear here after returning to this app.")
        findViewById<TextView>(R.id.debugStatus).text =
            "Debug Status: " + prefs.getString("debug", "No Paytm accessibility event received yet")
    }
}