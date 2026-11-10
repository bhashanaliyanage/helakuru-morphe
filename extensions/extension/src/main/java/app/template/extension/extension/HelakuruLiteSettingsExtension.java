package app.template.extension.extension;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;

/** Settings UI and drawing helpers for the Lite Frosted Glass patch. */
public final class HelakuruLiteSettingsExtension {
    private static final String ENABLED = "morphe_live_keyboard_blur_lite";
    private static final String BLUR_STRENGTH = "morphe_frosted_blur_strength";
    private static final String TRANSPARENCY_FOLLOWS_THEME = "morphe_frosted_transparency_follow_theme";
    private static final String TRANSPARENCY_AMOUNT = "morphe_frosted_transparency_amount";
    private static final String COLOR_THEME = "morphe_frosted_color_theme";
    private static final String KEY_SIZE = "morphe_key_background_size";
    private static final int DEFAULT_KEY_SIZE = 90;

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

    /** Computes an inset for the drawn keycap without changing the keyboard's hit box. */
    public static int getKeyBackgroundInset(Object viewObject, int keyHeight) {
        if (!(viewObject instanceof View) || keyHeight <= 0) return Math.max(0, keyHeight / 25);
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                    ((View) viewObject).getContext());
            int size = clamp(preferences.getInt(KEY_SIZE, DEFAULT_KEY_SIZE), 70, 100);
            return Math.round(keyHeight * (100 - size) / 200.0f);
        } catch (Throwable ignored) {
            return keyHeight / 25;
        }
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

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> callback.changed(seekBar.getProgress() + min))
                .show();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface ValueChanged {
        void changed(int value);
    }
}
