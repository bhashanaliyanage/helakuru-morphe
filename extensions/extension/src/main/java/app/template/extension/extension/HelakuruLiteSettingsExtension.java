package app.template.extension.extension;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.content.res.ColorStateList;
import android.inputmethodservice.Keyboard;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.graphics.Color;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Settings UI and drawing helpers for the Lite Frosted Glass patch. */
public final class HelakuruLiteSettingsExtension {
    private static final String ENABLED = "morphe_live_keyboard_blur_lite";
    private static final String BLUR_STRENGTH = "morphe_frosted_blur_strength";
    private static final String TRANSPARENCY_FOLLOWS_THEME = "morphe_frosted_transparency_follow_theme";
    private static final String TRANSPARENCY_AMOUNT = "morphe_frosted_transparency_amount";
    private static final String COLOR_THEME = "morphe_frosted_color_theme";
    private static final String KEY_SIZE = "morphe_key_background_size";
    private static final int DEFAULT_KEY_SIZE = 90;
    private static final Map<View, int[]> ORIGINAL_PADDING = new WeakHashMap<>();

    private HelakuruLiteSettingsExtension() {
    }

    public static void bindSettings(Object activityObject) {
        if (!(activityObject instanceof PreferenceActivity)) {
            return;
        }
        final PreferenceActivity activity = (PreferenceActivity) activityObject;
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor defaults = preferences.edit();
            if (!preferences.contains(BLUR_STRENGTH)) defaults.putInt(BLUR_STRENGTH, 32);
            if (!preferences.contains(TRANSPARENCY_FOLLOWS_THEME)) {
                defaults.putBoolean(TRANSPARENCY_FOLLOWS_THEME, true);
            }
            if (!preferences.contains(TRANSPARENCY_AMOUNT)) defaults.putInt(TRANSPARENCY_AMOUNT, 50);
            if (!preferences.contains(COLOR_THEME)) defaults.putString(COLOR_THEME, "monet");
            if (!preferences.contains(KEY_SIZE)) defaults.putInt(KEY_SIZE, DEFAULT_KEY_SIZE);
            defaults.apply();

            Preference blur = find(activity, BLUR_STRENGTH);
            Preference transparencyMode = find(activity, "morphe_frosted_transparency_mode");
            Preference transparencyAmount = find(activity, "morphe_frosted_transparency_amount");
            Preference colorTheme = find(activity, "morphe_frosted_color_theme");
            Preference enabled = find(activity, ENABLED);
            Preference frostPage = find(activity, "morphe_frosted_glass_page");
            if (frostPage instanceof android.preference.PreferenceScreen) {
                android.preference.PreferenceScreen screen = (android.preference.PreferenceScreen) frostPage;
                screen.setOnPreferenceClickListener(preference -> {
                    // PreferenceScreen creates its dialog after this click listener
                    // returns false, so look it up on the next UI-loop turn.
                    activity.getWindow().getDecorView().post(() -> {
                        Dialog dialog = screen.getDialog();
                        if (dialog != null && dialog.getWindow() != null) {
                            dialog.getWindow().getDecorView().post(() -> applyDialogInsets(dialog));
                        }
                    });
                    return false;
                });
            }
            Preference accent = find(activity, HelakuruLiteAccentChooser.PREFERENCE_KEY);
            if (accent != null) {
                HelakuruLiteAccentChooser.updateSummary(accent, preferences);
                accent.setEnabled(isEnabled(preferences)
                        && "monet".equals(preferences.getString(COLOR_THEME, "monet")));
                accent.setOnPreferenceClickListener(preference -> {
                    HelakuruLiteAccentChooser.show(activity, preferences, accent);
                    return true;
                });
            }

            if (blur != null) {
                updateNumberSummary(blur, preferences.getInt(BLUR_STRENGTH, 32), " dp");
                blur.setOnPreferenceClickListener(preference -> {
                    showSlider(activity, "Blur strength", " dp", 0, 80,
                            preferences.getInt(BLUR_STRENGTH, 32), value -> {
                                preferences.edit().putInt(BLUR_STRENGTH, value).apply();
                                updateNumberSummary(blur, value, " dp");
                            });
                    return true;
                });
            }

            if (transparencyMode != null) {
                updateTransparencyModeSummary(transparencyMode, preferences);
                transparencyMode.setOnPreferenceClickListener(preference -> {
                    new AlertDialog.Builder(activity)
                            .setTitle("Background transparency")
                            .setSingleChoiceItems(
                                    new String[]{"Follow Helakuru theme", "Custom amount"},
                                    preferences.getBoolean(TRANSPARENCY_FOLLOWS_THEME, true) ? 0 : 1,
                                    (dialog, which) -> {
                                        boolean follows = which == 0;
                                        preferences.edit().putBoolean(TRANSPARENCY_FOLLOWS_THEME, follows).apply();
                                        updateTransparencyModeSummary(transparencyMode, preferences);
                                        if (transparencyAmount != null) {
                                            transparencyAmount.setEnabled(!follows && isEnabled(preferences));
                                        }
                                        dialog.dismiss();
                                    })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
            }

            if (transparencyAmount != null) {
                updateNumberSummary(transparencyAmount,
                        preferences.getInt(TRANSPARENCY_AMOUNT, 50), "% transparent");
                transparencyAmount.setEnabled(
                        !preferences.getBoolean(TRANSPARENCY_FOLLOWS_THEME, true)
                                && isEnabled(preferences));
                transparencyAmount.setOnPreferenceClickListener(preference -> {
                    showSlider(activity, "Background transparency", "% transparent", 0, 100,
                            preferences.getInt(TRANSPARENCY_AMOUNT, 50), value -> {
                                preferences.edit().putInt(TRANSPARENCY_AMOUNT, value).apply();
                                updateNumberSummary(transparencyAmount, value, "% transparent");
                            });
                    return true;
                });
            }

            if (colorTheme != null) {
                updateColorSummary(colorTheme, preferences);
                colorTheme.setOnPreferenceClickListener(preference -> {
                    String[] choices = {"Monet", "Helakuru theme"};
                    int selected = "monet".equals(preferences.getString(COLOR_THEME, "monet")) ? 0 : 1;
                    new AlertDialog.Builder(activity)
                            .setTitle("Keyboard color theme")
                            .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                                preferences.edit().putString(COLOR_THEME, which == 0 ? "monet" : "helakuru").apply();
                                updateColorSummary(colorTheme, preferences);
                                setEnabled(accent, which == 0 && isEnabled(preferences));
                                dialog.dismiss();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
            }

            if (enabled != null) {
                enabled.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean active = Boolean.TRUE.equals(newValue);
                    setEnabled(transparencyMode, active);
                    setEnabled(accent, active && "monet".equals(preferences.getString(COLOR_THEME, "monet")));
                    if (transparencyAmount != null) {
                        transparencyAmount.setEnabled(active && !preferences.getBoolean(TRANSPARENCY_FOLLOWS_THEME, true));
                    }
                    if (colorTheme != null) colorTheme.setEnabled(active);
                    if (blur != null) blur.setEnabled(active && Build.VERSION.SDK_INT >= 31);
                    return true;
                });
                boolean active = preferences.getBoolean(ENABLED, false);
                setEnabled(transparencyMode, active);
                if (transparencyAmount != null) {
                    transparencyAmount.setEnabled(active && !preferences.getBoolean(TRANSPARENCY_FOLLOWS_THEME, true));
                }
                if (colorTheme != null) colorTheme.setEnabled(active);
                if (blur != null) {
                    blur.setEnabled(active && Build.VERSION.SDK_INT >= 31);
                    if (Build.VERSION.SDK_INT < 31) {
                        blur.setSummary("Requires Android 12 or later");
                    }
                }
            }
        } catch (Throwable ignored) {
            // Keep the stock settings screen usable if a preference differs in an app build.
        }
    }

    public static void bindThemeEditor(Object fragmentObject, Object rootObject) {
        if (!(rootObject instanceof View)) return;
        final View root = (View) rootObject;
        try {
            int id = root.getResources().getIdentifier("morphe_key_background_size", "id",
                    root.getContext().getPackageName());
            View control = root.findViewById(id);
            if (!(control instanceof TextView)) return;
            final TextView label = (TextView) control;
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(root.getContext());
            updateKeySizeLabel(label, preferences.getInt(KEY_SIZE, DEFAULT_KEY_SIZE));
            label.setOnClickListener(view -> showSlider(root.getContext(), "Key background size", "%", 70, 100,
                    preferences.getInt(KEY_SIZE, DEFAULT_KEY_SIZE), value -> {
                        preferences.edit().putInt(KEY_SIZE, value).apply();
                        updateKeySizeLabel(label, value);
                    }));
        } catch (Throwable ignored) {
            // The theme editor remains functional if this optional row cannot be bound.
        }
    }

    /** Scale only the drawable; the stock inset remains available to language labels/icons. */
    public static void drawKeyBackground(GradientDrawable drawable, Canvas canvas,
                                         Object viewObject, Keyboard.Key key) {
        boolean special = isLighterSpecialKey(key);
        ColorStateList stockFill = null;
        try {
            if (viewObject instanceof View) {
                if (special) {
                    // Lite's fills are already white overlays. A white RGB blend is
                    // invisible; compose an additional 12% white layer instead.
                    stockFill = getDrawableColor(drawable);
                    if (stockFill != null) {
                        drawable.setColor(ColorStateList.valueOf(blendTowardWhite(
                                stockFill.getDefaultColor(), 0.12f)));
                    }
                }
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                        ((View) viewObject).getContext());
                float scale = clamp(preferences.getInt(KEY_SIZE, DEFAULT_KEY_SIZE), 70, 100) / 100f;
                float centerX = key.x + key.width / 2f;
                float centerY = key.y + key.height / 2f;
                drawable.setBounds(Math.round(centerX - key.width * scale / 2f),
                        Math.round(centerY - key.height * scale / 2f),
                        Math.round(centerX + key.width * scale / 2f),
                        Math.round(centerY + key.height * scale / 2f));
            }
        } catch (Throwable ignored) {
            // Keep the bounds already computed by Lite if the preference cannot be read.
        }
        drawable.draw(canvas);
        if (special && stockFill != null) {
            drawable.setColor(stockFill);
        }
    }

    private static int blendTowardWhite(int color, float amount) {
        int oldAlpha = Color.alpha(color);
        int alpha = Math.round(oldAlpha + (255 - oldAlpha) * amount);
        float whiteContribution = amount * 255f;
        float retainedContribution = oldAlpha * (1f - amount);
        float total = whiteContribution + retainedContribution;
        int red = Math.round((Color.red(color) * retainedContribution + 255 * whiteContribution) / total);
        int green = Math.round((Color.green(color) * retainedContribution + 255 * whiteContribution) / total);
        int blue = Math.round((Color.blue(color) * retainedContribution + 255 * whiteContribution) / total);
        return Color.argb(alpha, red, green, blue);
    }

    private static ColorStateList getDrawableColor(GradientDrawable drawable) {
        try {
            Method method = GradientDrawable.class.getMethod("getColor");
            Object result = method.invoke(drawable);
            return result instanceof ColorStateList ? (ColorStateList) result : null;
        } catch (Throwable ignored) {
            // API 23 does not expose getColor(); leave its stock drawable untouched.
            return null;
        }
    }

    private static boolean isLighterSpecialKey(Keyboard.Key key) {
        if (key == null || key.codes == null || key.codes.length == 0) return false;
        switch (key.codes[0]) {
            case -1: // Shift
            case -2: // ?123 / symbol mode
            case -5: // Backspace
            case 10: // Return
            case 44: // Comma
            case 46: // Period
            case 84: // Search
                return true;
            default:
                return false;
        }
    }

    public static void drawKeyLabel(Canvas canvas, String label, Keyboard.Key key,
                                    float stockBaseline, Paint paint) {
        int code = key.codes != null && key.codes.length > 0 ? key.codes[0] : 0;
        float centerX = key.x + key.width / 2f;
        if (code == 32 || code == -3) {
            canvas.drawText(label, centerX, stockBaseline, paint);
            return;
        }
        Rect bounds = new Rect();
        paint.getTextBounds(label, 0, label.length(), bounds);
        float baseline;
        float x;
        if (bounds.isEmpty()) {
            Paint.FontMetrics metrics = paint.getFontMetrics();
            baseline = key.y + key.height / 2f - (metrics.ascent + metrics.descent) / 2f;
            x = centerX;
        } else {
            baseline = key.y + key.height / 2f - (bounds.top + bounds.bottom) / 2f;
            // getTextBounds uses a left-aligned origin; account for the paint's alignment.
            float advance = paint.measureText(label);
            float alignment = paint.getTextAlign() == Paint.Align.CENTER ? advance / 2f
                    : paint.getTextAlign() == Paint.Align.RIGHT ? advance : 0f;
            x = centerX - (bounds.left + bounds.right) / 2f + alignment;
        }
        canvas.drawText(label, x, baseline, paint);
    }

    /** Applies the more rounded Gboard-like silhouette to mode and action keys. */
    public static void applyActionKeyStyle(Object viewObject, Object keyObject) {
        if (viewObject == null || keyObject == null) return;
        try {
            Field codesField = keyObject.getClass().getField("codes");
            int[] codes = (int[]) codesField.get(keyObject);
            if (codes == null || codes.length == 0) return;
            int code = codes[0];
            int width = keyObject.getClass().getField("width").getInt(keyObject);
            int height = keyObject.getClass().getField("height").getInt(keyObject);
            // These drawables are shared by every key, so restore the normal
            // rounded-rectangle radius before styling the next key in the loop.
            float radius = (code == -2 || code == 10 || code == 84)
                    ? Math.min(width, height) * 0.36f : 10.0f;
            setDrawableRadius(viewObject, "keyBackground", radius);
            setDrawableRadius(viewObject, "keyBackgroundPressed", radius);
        } catch (Throwable ignored) {
            // The stock drawing path remains available if Lite changes these internals.
        }
    }

    private static void setDrawableRadius(Object viewObject, String fieldName, float radius) throws Exception {
        Field field = viewObject.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object drawable = field.get(viewObject);
        if (drawable instanceof GradientDrawable) {
            ((GradientDrawable) drawable).setCornerRadius(radius);
        }
    }

    private static Preference find(PreferenceActivity activity, String key) {
        return activity.findPreference(key);
    }

    private static void setEnabled(Preference preference, boolean enabled) {
        if (preference != null) preference.setEnabled(enabled);
    }

    private static boolean isEnabled(SharedPreferences preferences) {
        return preferences.getBoolean(ENABLED, false);
    }

    private static void updateTransparencyModeSummary(Preference preference, SharedPreferences preferences) {
        preference.setSummary(preferences.getBoolean(TRANSPARENCY_FOLLOWS_THEME, true)
                ? "Follow Helakuru theme" : "Use a custom transparency amount");
    }

    private static void updateColorSummary(Preference preference, SharedPreferences preferences) {
        preference.setSummary("monet".equals(preferences.getString(COLOR_THEME, "monet"))
                ? "Monet" : "Helakuru theme");
    }

    private static void updateNumberSummary(Preference preference, int value, String suffix) {
        preference.setSummary(value + suffix);
    }

    private static void updateKeySizeLabel(TextView label, int value) {
        label.setText("Key background size · " + value + "%");
    }

    private static void showSlider(Context context, String title, String suffix, int min, int max,
                                   int current, ValueChanged callback) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView valueLabel = new TextView(context);
        valueLabel.setText((suffix.startsWith("%") ? "" : "") + current + suffix);
        layout.addView(valueLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamp(current, min, max) - min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueLabel.setText((progress + min) + suffix);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        layout.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialogInterface, which) -> callback.changed(seekBar.getProgress() + min))
                .create();
        dialog.setOnShowListener(ignored -> applyDialogInsets(dialog));
        dialog.show();
    }

    public static void applyDialogInsets(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        View content = window.findViewById(android.R.id.content);
        if (content == null) content = window.getDecorView();
        final View insetView = content;
        insetView.setOnApplyWindowInsetsListener((view, insets) -> {
            int[] base;
            synchronized (ORIGINAL_PADDING) {
                base = ORIGINAL_PADDING.get(view);
                if (base == null) {
                    base = new int[]{view.getPaddingLeft(), view.getPaddingTop(),
                            view.getPaddingRight(), view.getPaddingBottom()};
                    ORIGINAL_PADDING.put(view, base);
                }
            }
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
            }
            view.setPadding(base[0], base[1] + top, base[2], base[3] + bottom);
            return insets;
        });
        insetView.requestApplyInsets();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface ValueChanged {
        void changed(int value);
    }
}
