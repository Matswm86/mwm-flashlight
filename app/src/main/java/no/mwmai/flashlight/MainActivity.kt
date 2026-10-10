package no.mwmai.flashlight

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.mwmai.flashlight.ui.FlashlightScreen
import no.mwmai.flashlight.ui.MwmFlashlightTheme
import no.mwmai.flashlight.ui.ScreenLight

class MainActivity : ComponentActivity() {
    private lateinit var torch: Torch
    private val prefs by lazy { getSharedPreferences("flashlight", MODE_PRIVATE) }

    // What the user asked for. The flash itself blinks under this in STROBE
    // and SOS, so the torch's own on/off state is not the same thing.
    private var lit by mutableStateOf(false)
    private var mode by mutableStateOf(Mode.STEADY)
    private var level by mutableIntStateOf(1)
    private var hz by mutableIntStateOf(DEFAULT_STROBE_HZ)
    private var refused by mutableStateOf(false)
    private var screenLight by mutableStateOf(false)
    private var pattern: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.dark(0xFF14171C.toInt())
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)

        torch = Torch(this)
        mode = runCatching { Mode.valueOf(prefs.getString("mode", null) ?: "") }.getOrDefault(Mode.STEADY)
        level = prefs.getInt("level", torch.defaultLevel).coerceIn(1, torch.maxLevel)
        hz = prefs.getInt("hz", DEFAULT_STROBE_HZ).coerceIn(MIN_STROBE_HZ, MAX_STROBE_HZ)
        torch.listen()

        // Keep the app in step when the flash is switched from outside, for
        // example from the quick settings tile, or was left on last time.
        lifecycleScope.launch {
            torch.state.collect { state ->
                if (mode == Mode.STEADY && pattern == null && state.on != lit) lit = state.on
                if (state.busy && lit) {
                    lit = false
                    apply()
                }
            }
        }

        setContent {
            MwmFlashlightTheme {
                BackHandler(enabled = screenLight) { showScreenLight(false) }
                if (screenLight) {
                    ScreenLight(onClose = { showScreenLight(false) })
                } else {
                    val torchState by torch.state.collectAsStateWithLifecycle()
                    FlashlightScreen(
                        lit = lit,
                        mode = mode,
                        hasFlash = torch.available,
                        busy = torchState.busy || refused,
                        level = level,
                        maxLevel = torch.maxLevel,
                        hz = hz,
                        onPower = ::togglePower,
                        onMode = ::switchMode,
                        onLevel = ::changeLevel,
                        onHz = ::changeHz,
                        onScreenLight = { showScreenLight(true) },
                    )
                }
            }
        }
    }

    override fun onStop() {
        // A blinking pattern needs this screen to drive it. A steady light is
        // left on, like the quick settings tile does.
        if (mode != Mode.STEADY && lit) {
            lit = false
            apply()
        }
        super.onStop()
    }

    override fun onDestroy() {
        torch.unlisten()
        super.onDestroy()
    }

    private fun togglePower() {
        if (!torch.available) {
            showScreenLight(true)
            return
        }
        lit = !lit
        apply()
    }

    private fun switchMode(new: Mode) {
        mode = new
        prefs.edit { putString("mode", new.name) }
        apply()
    }

    private fun changeLevel(new: Int) {
        level = new
        prefs.edit { putInt("level", new) }
        if (lit && mode == Mode.STEADY) torch.set(true, level)
    }

    private fun changeHz(new: Int) {
        hz = new
        prefs.edit { putInt("hz", new) }
        if (lit && mode == Mode.STROBE) apply()
    }

    /** Drives the flash to match [lit] and [mode]. */
    private fun apply() {
        stopPattern()
        refused = false
        if (!lit) {
            torch.set(false)
            updateKeepScreenOn()
            return
        }
        when (mode) {
            Mode.STEADY -> if (!torch.set(true, level)) {
                lit = false
                refused = true
            }
            Mode.STROBE -> runPattern { strobeSteps(hz) }
            Mode.SOS -> runPattern { sosSteps() }
        }
        updateKeepScreenOn()
    }

    // Everything runs on the main thread, so once stopPattern() cancels the
    // job it cannot flip the flash again after apply() has set it.
    private fun runPattern(steps: () -> List<Step>) {
        pattern = lifecycleScope.launch {
            while (true) {
                for (step in steps()) {
                    if (!torch.set(step.on, level)) {
                        lit = false
                        refused = true
                        stopPattern()
                        updateKeepScreenOn()
                        return@launch
                    }
                    delay(step.millis)
                }
            }
        }
    }

    private fun stopPattern() {
        val job = pattern ?: return
        pattern = null
        job.cancel()
        torch.set(false)
    }

    private fun showScreenLight(on: Boolean) {
        screenLight = on
        // Full brightness for this window only; the system setting is untouched.
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        updateKeepScreenOn()
    }

    // The screen must stay awake while it is the light, or while it drives a
    // blinking pattern. A steady flash does not need it.
    private fun updateKeepScreenOn() {
        val keep = screenLight || (lit && mode != Mode.STEADY)
        if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
