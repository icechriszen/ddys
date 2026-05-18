package com.jing.ddys.playback

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRemoteFocusTest {

    @Test
    fun dpadUpMovesFocusToTopControlsWhenControlsAreVisible() {
        assertTrue(
            PlaybackRemoteFocus.shouldMoveFocusToTopControls(
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                action = KeyEvent.ACTION_DOWN,
                controlsVisible = true
            )
        )
    }

    @Test
    fun dpadUpDoesNotMoveFocusToTopControlsWhenControlsAreHidden() {
        assertFalse(
            PlaybackRemoteFocus.shouldMoveFocusToTopControls(
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                action = KeyEvent.ACTION_DOWN,
                controlsVisible = false
            )
        )
    }

    @Test
    fun keyUpDoesNotMoveFocusToTopControls() {
        assertFalse(
            PlaybackRemoteFocus.shouldMoveFocusToTopControls(
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                action = KeyEvent.ACTION_UP,
                controlsVisible = true
            )
        )
    }
}
