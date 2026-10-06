package com.example

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.service.LassoOverlayService
import com.example.service.MediaProjectionHolder

/** A consent-only activity; finishing returns to the app behind the existing chat. */
class ScreenCapturePermissionActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionHolder.resultCode = result.resultCode
            MediaProjectionHolder.resultData = result.data
            ContextCompat.startForegroundService(this, Intent(this, LassoOverlayService::class.java)
                .setAction(LassoOverlayService.ACTION_OPEN_SELECTION))
        } else {
            Toast.makeText(this, R.string.error_no_projection, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            consent.launch(MediaProjectionHolder.createCaptureIntent(manager))
        }
    }
}
