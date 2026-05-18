package com.jing.ddys.playback

import android.view.KeyEvent

internal object PlaybackRemoteFocus {
    fun shouldMoveFocusToTopControls(
        keyCode: Int,
        action: Int,
        controlsVisible: Boolean
    ): Boolean {
        return controlsVisible &&
            action == KeyEvent.ACTION_DOWN &&
            keyCode == KeyEvent.KEYCODE_DPAD_UP
    }
}
