package app.template.patches.example

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.COMPATIBILITY_HELAKURU

private const val EXTENSION_CLASS = "Lapp/template/extension/extension/LiveBlurExtension;"

private val liveBlurPreferenceResourcePatch = resourcePatch {
    execute {
        document("res/xml/new_preferences.xml").use { preferences ->
            val androidNamespace = "http://schemas.android.com/apk/res/android"
            val categories = preferences.getElementsByTagName("PreferenceCategory")
            val themesCategory = (0 until categories.length)
                .map { categories.item(it) as org.w3c.dom.Element }
                .first { it.getAttributeNS(androidNamespace, "key") == "pref3" }

            val preference = preferences.createElement("SwitchPreference")
            preference.setAttributeNS(androidNamespace, "android:key", "morphe_live_keyboard_blur")
            preference.setAttributeNS(androidNamespace, "android:title", "Live background blur")
            preference.setAttributeNS(
                androidNamespace,
                "android:summary",
                "Blur the content behind the keyboard (Android 12+)"
            )
            preference.setAttributeNS(androidNamespace, "android:defaultValue", "false")
            themesCategory.appendChild(preference)
        }
    }
}

@Suppress("unused")
val liveKeyboardBlurPatch = patch(
    name = "Live Keyboard Background Blur",
    description = "Adds a setting to blur the content behind the Helakuru keyboard.",
    default = true
) {
    compatibleWith(COMPATIBILITY_HELAKURU)
    dependsOn(liveBlurPreferenceResourcePatch)
    extendWith("extensions/extension.mpe")

    execute {
        val settingsSuperCall = KeyboardSettingsFingerprint.instructionMatches.single()
        KeyboardSettingsFingerprint.method.addInstructions(
            settingsSuperCall.index + 1,
            "invoke-static {p0}, $EXTENSION_CLASS->bindSettings(Ljava/lang/Object;)V"
        )

        KeyboardWindowFingerprint.method.addInstructions(
            0,
            "invoke-static {p0}, $EXTENSION_CLASS->applyKeyboardBlur(Ljava/lang/Object;)V"
        )
    }
}
