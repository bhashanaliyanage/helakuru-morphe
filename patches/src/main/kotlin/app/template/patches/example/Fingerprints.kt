package app.template.patches.example

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/** Helakuru's IME entry point for applying its keyboard window colors. */
object KeyboardWindowFingerprint : Fingerprint(
    definingClass = "Llk/bhasha/helakuru/SinhalaSoftKeyboard;",
    name = "setKeyboardAndNavBarColor",
    returnType = "V"
)

/** Inject after the base fragment has created the settings preference view. */
object KeyboardSettingsFingerprint : Fingerprint(
    definingClass = "Llk/bhasha/helakuru/fragment/SettingFragment;",
    name = "onViewCreated",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = "Landroidx/preference/PreferenceFragmentCompat;",
            name = "onViewCreated"
        )
    )
)

/** Helakuru Lite's keyboard lifecycle callback, called whenever the IME is shown. */
object HelakuruLiteKeyboardWindowFingerprint : Fingerprint(
    definingClass = "Llk/bhasha/helakuru/lite/keyboard/KeyboardService;",
    name = "onStartInputView",
    parameters = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
    returnType = "V"
)

// Retained for the template's internal example patch.
object AdLoaderFingerprint : Fingerprint(
    definingClass = "Lcom/some/app/ads/AdsLoader;",
    name = "showAds",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "I", "L"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET, definingClass = "this", type = "Ljava/util/Map;"),
        string("showBannerAds"),
        methodCall(definingClass = "Ljava/lang/String;", name = "equals"),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        literal(1337),
        opcode(Opcode.IF_EQ)
    )
)
