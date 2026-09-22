package com.csk4.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CSK4_CALL"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var lastNumber = ""
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        try {
            // Initialize CallRecorder context
            CallRecorder.init(context)
            
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
            
            val newState = when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                else -> return
            }
            
            if (newState == lastState && number == lastNumber) return
            
            Log.d(TAG, "Call state: $state, number: $number")
            
            // Initiate call recording
            CallRecorder(context).onCallStateChanged(newState, number)
            
            lastState = newState
            if (number.isNotEmpty()) lastNumber = number
            
        } catch (e: Exception) {
            Log.e(TAG, "CallReceiver: ${e.message}")
        }
    }
}
