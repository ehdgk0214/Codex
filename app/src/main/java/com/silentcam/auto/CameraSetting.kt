package com.silentcam.auto

object CameraSetting {
    const val KEY = "csc_pref_camera_forced_shuttersound_key"
    const val DISABLE_FORCED_SHUTTER_SOUND_COMMAND = "settings put system $KEY 0"
    const val READ_COMMAND = "settings get system $KEY"
}
