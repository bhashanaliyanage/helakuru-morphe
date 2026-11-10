package app.template.extension.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Runtime blur and tint behavior for the Helakuru Lite Frosted Glass settings. */
public final class HelakuruLiteLiveBlurExtension {
    private static final String ENABLED = "morphe_live_keyboard_blur_lite";
    private static final String BLUR_STRENGTH = "morphe_frosted_blur_strength";
    private static final String TRANSPARENCY_FOLLOWS_THEME = "morphe_frosted_transparency_follow_theme";
    private static final String TRANSPARENCY_AMOUNT = "morphe_frosted_transparency_amount";
    private static final String COLOR_THEME = "morphe_frosted_color_theme";
    private static final int DEFAULT_BLUR_DP = 32;
    private static final int DEFAULT_TRANSPARENCY = 50;
    private static final Map<Window, WindowState> STATES = new WeakHashMap<>();

    private HelakuruLiteLiveBlurExtension() {
    }

    public static void applyKeyboardBlur(Object serviceObject) {
        if (!(serviceObject instanceof InputMethodService)) return;
        InputMethodService service = (InputMethodService) serviceObject;
        try {
            Window window = service.getWindow() == null ? null : service.getWindow().getWindow();
            if (window == null) return;

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(service);
            if (!preferences.getBoolean(ENABLED, false)) {
                restore(window, service);
                return;
            }

            View root = getRootView(service);
            View toolbar = findCandidateContainer(service, root);
            synchronized (STATES) {
                if (!STATES.containsKey(window)) {
                    STATES.put(window, new WindowState(
                            getBackgroundBlurRadius(window),
                            window.getDecorView().getBackground(),
                            root == null ? null : root.getBackground(),
                            toolbar == null ? null : toolbar.getBackground()));
                }
            }

            if (Build.VERSION.SDK_INT >= 31 && isBlurAvailable(service)) {
                int blurDp = clamp(preferences.getInt(BLUR_STRENGTH, DEFAULT_BLUR_DP), 0, 80);
                int radius = Math.round(blurDp * service.getResources().getDisplayMetrics().density);
                window.getClass().getMethod("setBackgroundBlurRadius", int.class)
                        .invoke(window, radius);
            }
            window.setBackgroundDrawableResource(android.R.color.transparent);

            int tint = getTintColor(service, preferences);
            int transparency = getTransparency(service, preferences);
            int alpha = Math.round((100 - transparency) * 2.55f);
            if (root != null) root.setBackground(new ColorDrawable((tint & 0x00ffffff) | (alpha << 24)));
            if (toolbar != null) toolbar.setBackgroundColor(Color.TRANSPARENT);
        } catch (Throwable ignored) {
            // A platform-specific effect failure must not stop the keyboard from working.
        }
    }

    private static int getTintColor(Context context, SharedPreferences preferences) {
        if ("monet".equals(preferences.getString(COLOR_THEME, "monet"))) {
            try {
                int id = context.getResources().getIdentifier("system_accent1_600", "color", "android");
                if (id != 0) return context.getResources().getColor(id);
            } catch (Throwable ignored) {
                // Use the current Helakuru theme as a fallback on builds without Monet resources.
            }
        }
        return getHelakuruBackgroundColor(context);
    }

    private static int getTransparency(Context context, SharedPreferences preferences) {
        if (!preferences.getBoolean(TRANSPARENCY_FOLLOWS_THEME, true)) {
            return clamp(preferences.getInt(TRANSPARENCY_AMOUNT, DEFAULT_TRANSPARENCY), 0, 100);
        }
        int themeColor = getHelakuruBackgroundColor(context);
        double luminance = (0.2126 * Color.red(themeColor) + 0.7152 * Color.green(themeColor)
                + 0.0722 * Color.blue(themeColor)) / 255.0;
        return luminance < 0.45 ? 82 : 90;
    }

    private static int getHelakuruBackgroundColor(Context context) {
        try {
            Class<?> serviceClass = Class.forName("lk.bhasha.helakuru.lite.keyboard.KeyboardService");
            Field color = serviceClass.getField("keyBackgroundColor");
            return color.getInt(null);
        } catch (Throwable ignored) {
            return Color.DKGRAY;
        }
    }

    private static boolean isBlurAvailable(Context context) {
        if (Build.VERSION.SDK_INT < 31) return false;
        try {
            Object manager = context.getSystemService(Context.WINDOW_SERVICE);
            Method method = manager.getClass().getMethod("isCrossWindowBlurEnabled");
            return (Boolean) method.invoke(manager);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int getBackgroundBlurRadius(Window window) {
        try {
            return (Integer) window.getClass().getMethod("getBackgroundBlurRadius").invoke(window);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static View getRootView(InputMethodService service) {
        try {
            Field field = service.getClass().getDeclaredField("rootView");
            field.setAccessible(true);
            return (View) field.get(service);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View findCandidateContainer(Context context, View root) {
        if (root == null) return null;
        int id = context.getResources().getIdentifier("candidate_container", "id", context.getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    private static void restore(Window window, InputMethodService service) {
        WindowState state;
        synchronized (STATES) {
            state = STATES.remove(window);
        }
        if (state == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                window.getClass().getMethod("setBackgroundBlurRadius", int.class)
                        .invoke(window, state.blurRadius);
            }
            window.setBackgroundDrawable(state.windowBackground);
            View root = getRootView(service);
            if (root != null) root.setBackground(state.rootBackground);
            View toolbar = findCandidateContainer(service, root);
            if (toolbar != null) toolbar.setBackground(state.toolbarBackground);
        } catch (Throwable ignored) {
            // Ignore unsupported window restoration APIs.
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class WindowState {
        final int blurRadius;
        final Drawable windowBackground;
        final Drawable rootBackground;
        final Drawable toolbarBackground;

        WindowState(int blurRadius, Drawable windowBackground, Drawable rootBackground,
                    Drawable toolbarBackground) {
            this.blurRadius = blurRadius;
            this.windowBackground = windowBackground;
            this.rootBackground = rootBackground;
            this.toolbarBackground = toolbarBackground;
        }
    }
}
