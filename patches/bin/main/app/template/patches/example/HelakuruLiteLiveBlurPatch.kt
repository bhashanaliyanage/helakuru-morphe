package app.template.patches.example

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.COMPATIBILITY_HELAKURU_LITE

private const val LITE_BLUR_EXTENSION =
    "Lapp/template/extension/extension/HelakuruLiteLiveBlurExtension;"
private const val LITE_BLUR_KEY = "morphe_live_keyboard_blur_lite"

private val helakuruLiteBlurPreferencePatch = resourcePatch {
    execute {
        document("res/xml/ime_preferences.xml").use { preferences ->
            val androidNamespace = "http://schemas.android.com/apk/res/android"
            val categories = preferences.getElementsByTagName("PreferenceCategory")
            val generalSettings = (0 until categories.length)
                .map { categories.item(it) as org.w3c.dom.Element }
                .first { it.getAttributeNS(androidNamespace, "key") == "pref1" }

            val switch = preferences.createElement("SwitchPreference")
            switch.setAttributeNS(androidNamespace, "android:key", LITE_BLUR_KEY)
            switch.setAttributeNS(androidNamespace, "android:title", "Live background blur")
            switch.setAttributeNS(
                androidNamespace,
                "android:summary",
                "Blur the content behind the keyboard (Android 12+)."
            )
            switch.setAttributeNS(androidNamespace, "android:defaultValue", "false")
            generalSettings.appendChild(switch)
        }
    }
}

@Suppress("unused")
val helakuruLiteLiveKeyboardBlurPatch = patch(
    name = "Live Keyboard Background Blur (Helakuru Lite)",
    description = "Adds a setting to blur the content behind the Helakuru Lite keyboard.",
    default = true
) {
    compatibleWith(COMPATIBILITY_HELAKURU_LITE)
    dependsOn(helakuruLiteBlurPreferencePatch)
    extendWith("extensions/extension.mpe")

    execute {
        HelakuruLiteKeyboardWindowFingerprint.method.addInstructions(
            0,
            "invoke-static {p0}, $LITE_BLUR_EXTENSION->applyKeyboardBlur(Ljava/lang/Object;)V"
        )
    }
}
