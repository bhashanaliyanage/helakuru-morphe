package app.template.patches.example

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.COMPATIBILITY_HELAKURU_LITE
import org.w3c.dom.Element

private const val LITE_EXTENSION = "Lapp/template/extension/extension/HelakuruLiteLiveBlurExtension;"
private const val LITE_SETTINGS_EXTENSION = "Lapp/template/extension/extension/HelakuruLiteSettingsExtension;"
private const val LITE_SETTINGS_KEY = "morphe_live_keyboard_blur_lite"

private val helakuruLiteFrostedGlassPreferencesPatch = resourcePatch {
    execute {
        document("res/xml/ime_preferences.xml").use { preferences ->
            val androidNamespace = "http://schemas.android.com/apk/res/android"
            val categories = preferences.getElementsByTagName("PreferenceCategory")
            val generalSettings = (0 until categories.length)
                .map { categories.item(it) as Element }
                .first { it.getAttributeNS(androidNamespace, "key") == "pref1" }

            val page = preferences.createElement("PreferenceScreen") as Element
            page.setAttributeNS(androidNamespace, "android:key", "morphe_frosted_glass_page")
            page.setAttributeNS(androidNamespace, "android:title", "Frosted Glass")
            page.setAttributeNS(
                androidNamespace,
                "android:summary",
                "Blur, transparency, and keyboard color"
            )

            val enabled = preferences.createElement("SwitchPreference") as Element
            enabled.setAttributeNS(androidNamespace, "android:key", LITE_SETTINGS_KEY)
            enabled.setAttributeNS(androidNamespace, "android:title", "Enable Frosted Glass")
            enabled.setAttributeNS(
                androidNamespace,
                "android:summary",
                "Blur and tint the keyboard background"
            )
            enabled.setAttributeNS(androidNamespace, "android:defaultValue", "false")
            page.appendChild(enabled)

            addPreference(preferences, page, androidNamespace, "morphe_frosted_blur_strength", "Blur strength", "32 dp")
            addPreference(preferences, page, androidNamespace, "morphe_frosted_transparency_mode", "Background transparency", "Follow Helakuru theme")
            addPreference(preferences, page, androidNamespace, "morphe_frosted_transparency_amount", "Custom transparency", "50%")
            addPreference(preferences, page, androidNamespace, "morphe_frosted_color_theme", "Color theme", "Monet")

            addPreference(preferences, page, androidNamespace, "morphe_frosted_accent", "Accent color", "Accent 1 / 900")

            generalSettings.appendChild(page)
        }
    }
}

private val helakuruLiteKeyBackgroundSizeResourcePatch = resourcePatch {
    execute {
        document("res/layout/fragment_customize_theme.xml").use { layout ->
            val androidNamespace = "http://schemas.android.com/apk/res/android"
            val root = layout.documentElement
            val settingsRow = (0 until root.childNodes.length)
                .mapNotNull { root.childNodes.item(it) as? Element }
                .first { it.tagName == "LinearLayout" }

            settingsRow.setAttributeNS(androidNamespace, "android:id", "@+id/morphe_key_controls_row")
            settingsRow.setAttributeNS(androidNamespace, "android:layout_height", "wrap_content")

            val sizeControl = layout.createElement("TextView") as Element
            sizeControl.setAttributeNS(androidNamespace, "android:id", "@+id/morphe_key_background_size")
            sizeControl.setAttributeNS(androidNamespace, "android:layout_width", "match_parent")
            sizeControl.setAttributeNS(androidNamespace, "android:layout_height", "wrap_content")
            sizeControl.setAttributeNS(androidNamespace, "android:layout_below", "@id/morphe_key_controls_row")
            sizeControl.setAttributeNS(androidNamespace, "android:layout_marginLeft", "16dp")
            sizeControl.setAttributeNS(androidNamespace, "android:layout_marginStart", "16dp")
            sizeControl.setAttributeNS(androidNamespace, "android:layout_marginTop", "8dp")
            sizeControl.setAttributeNS(androidNamespace, "android:padding", "8dp")
            sizeControl.setAttributeNS(androidNamespace, "android:text", "Key background size · 90%")
            sizeControl.setAttributeNS(androidNamespace, "android:textColor", "@color/white")
            sizeControl.setAttributeNS(androidNamespace, "android:textSize", "14sp")
            sizeControl.setAttributeNS(androidNamespace, "android:clickable", "true")
            sizeControl.setAttributeNS(androidNamespace, "android:focusable", "true")
            root.appendChild(sizeControl)

            val tileColor = sizeControl.cloneNode(false) as Element
            tileColor.setAttributeNS(androidNamespace, "android:id", "@+id/morphe_key_tile_color")
            tileColor.setAttributeNS(androidNamespace, "android:layout_below", "@id/morphe_key_background_size")
            tileColor.setAttributeNS(androidNamespace, "android:text", "Key tile color · Follow theme")
            root.appendChild(tileColor)

            val tileTransparency = sizeControl.cloneNode(false) as Element
            tileTransparency.setAttributeNS(androidNamespace, "android:id", "@+id/morphe_key_tile_transparency")
            tileTransparency.setAttributeNS(androidNamespace, "android:layout_below", "@id/morphe_key_tile_color")
            tileTransparency.setAttributeNS(androidNamespace, "android:text", "Key tile transparency · 82%")
            root.appendChild(tileTransparency)
        }
    }
}

private fun addPreference(
    document: org.w3c.dom.Document,
    page: Element,
    androidNamespace: String,
    key: String,
    title: String,
    summary: String
) {
    val preference = document.createElement("Preference") as Element
    preference.setAttributeNS(androidNamespace, "android:key", key)
    preference.setAttributeNS(androidNamespace, "android:title", title)
    preference.setAttributeNS(androidNamespace, "android:summary", summary)
    page.appendChild(preference)
}

@Suppress("unused")
val helakuruLiteLiveKeyboardBlurPatch = patch(
    name = "Frosted Glass (Helakuru Lite)",
    description = "Adds adjustable keyboard blur, transparency, Monet tint, and Gboard-style key backgrounds.",
    default = true
) {
    compatibleWith(COMPATIBILITY_HELAKURU_LITE)
    dependsOn(helakuruLiteFrostedGlassPreferencesPatch, helakuruLiteKeyBackgroundSizeResourcePatch)
    extendWith("extensions/extension.mpe")

    execute {
        val lifecycleInstructions = HelakuruLiteKeyboardWindowFingerprint.method.implementation!!.instructions.toList()
        val lifecycleReturn = lifecycleInstructions.indexOfLast {
            it.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID
        }
        check(lifecycleReturn >= 0) { "Could not locate the Lite keyboard lifecycle return" }
        HelakuruLiteKeyboardWindowFingerprint.method.addInstructions(
            lifecycleReturn,
            "invoke-static {p0}, $LITE_EXTENSION->applyKeyboardBlur(Ljava/lang/Object;)V"
        )

        val settings = HelakuruLiteSettingsCreateFingerprint.instructionMatches.single()
        HelakuruLiteSettingsCreateFingerprint.method.addInstructions(
            settings.index + 1,
            "invoke-static {p0}, $LITE_SETTINGS_EXTENSION->bindSettings(Ljava/lang/Object;)V"
        )

        val themeEditor = HelakuruLiteThemeEditorViewFingerprint.instructionMatches.single()
        HelakuruLiteThemeEditorViewFingerprint.method.addInstructions(
            themeEditor.index + 1,
            "invoke-static {p0, p1}, $LITE_SETTINGS_EXTENSION->bindThemeEditor(Ljava/lang/Object;Ljava/lang/Object;)V"
        )

        val drawInstructions = HelakuruLiteKeyboardViewDrawFingerprint.method.implementation!!.instructions.toList()
        // Apply edits from the end so inserting calls cannot invalidate earlier indices.
        val edits = drawInstructions.mapIndexedNotNull { index, instruction ->
            val reference = (instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                ?.reference?.toString()
            when (reference) {
                "Landroid/graphics/drawable/GradientDrawable;->draw(Landroid/graphics/Canvas;)V" ->
                    index to "invoke-static {v1, v9, v0, v7}, $LITE_SETTINGS_EXTENSION->drawKeyBackground(Landroid/graphics/drawable/GradientDrawable;Landroid/graphics/Canvas;Ljava/lang/Object;Landroid/inputmethodservice/Keyboard\$Key;)V"
                "Landroid/graphics/Canvas;->drawText(Ljava/lang/String;FFLandroid/graphics/Paint;)V" ->
                    index to "invoke-static {v9, v1, v7, v6, v2}, $LITE_SETTINGS_EXTENSION->drawKeyLabel(Landroid/graphics/Canvas;Ljava/lang/String;Landroid/inputmethodservice/Keyboard\$Key;FLandroid/graphics/Paint;)V"
                else -> null
            }
        }
        check(edits.size == 3) { "Expected two key backgrounds and one key label draw call" }
        edits.reversed().forEach { (index, replacement) ->
            HelakuruLiteKeyboardViewDrawFingerprint.method.replaceInstruction(index, replacement)
        }
        val keyCastIndex = drawInstructions.indexOfFirst { instruction ->
            instruction.opcode == com.android.tools.smali.dexlib2.Opcode.CHECK_CAST &&
                (instruction as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                    ?.reference?.toString() == "Landroid/inputmethodservice/Keyboard\$Key;"
        }
        check(keyCastIndex >= 0) { "Could not locate the Lite keyboard key drawing loop" }
        HelakuruLiteKeyboardViewDrawFingerprint.method.addInstructions(
            keyCastIndex + 1,
            "invoke-static {v0, v7}, $LITE_SETTINGS_EXTENSION->applyActionKeyStyle(Ljava/lang/Object;Ljava/lang/Object;)V"
        )
    }
}
