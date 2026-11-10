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

/** SettingsActivity callback after its preference XML has been inflated. */
object HelakuruLiteSettingsCreateFingerprint : Fingerprint(
    definingClass = "Llk/bhasha/helakuru/lite/setting/SettingsActivity;",
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = "Landroid/preference/PreferenceActivity;",
            name = "addPreferencesFromResource"
        )
    )
)

/** Theme customization view containing Lite's existing Key border switch. */
object HelakuruLiteThemeEditorViewFingerprint : Fingerprint(
    definingClass = "Llk/bhasha/helakuru/lite/theme/fragment/ThemeCustomizeFragment;",
    name = "u",
    parameters = listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;"),
    returnType = "Landroid/view/View;",
    filters = listOf(
        methodCall(
            definingClass = "Landroid/widget/CompoundButton;",
            name = "setOnCheckedChangeListener"
        )
    )
)

/** The dimension-derived inset used solely for drawing key backgrounds. */
object HelakuruLiteKeyboardViewInsetFingerprint : Fingerprint(
    definingClass = "Llk/bhasha/helakuru/lite/keyboard/KeyboardView;",
    name = "onDraw",
    parameters = listOf("Landroid/graphics/Canvas;"),
    returnType = "V",
    filters = listOf(literal(25))
)

/** Key iteration hook for giving action keys their own rounded shape. */
object HelakuruLiteKeyboardViewDrawFingerprint : Fingerprint(
    definingClass = "Llk/bhasha/helakuru/lite/keyboard/KeyboardView;",
    name = "onDraw",
    parameters = listOf("Landroid/graphics/Canvas;"),
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
