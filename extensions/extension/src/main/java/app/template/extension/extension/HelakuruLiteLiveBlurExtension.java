package app.template.extension.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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

/** Runtime support for the Helakuru Lite live keyboard backdrop blur patch. */
public final class HelakuruLiteLiveBlurExtension {
    private static final String PREFERENCE_KEY = "morphe_live_keyboard_blur_lite";
    private static final int BLUR_RADIUS_DP = 32;
    private static final Map<Window, WindowState> STATES = new WeakHashMap<>();

    private HelakuruLiteLiveBlurExtension() {
    }

    public static void applyKeyboardBlur(Object serviceObject) {
        if (!(serviceObject instanceof InputMethodService)) {
            return;
        }
        InputMethodService service = (InputMethodService) serviceObject;
        try {
            Window window = service.getWindow() == null
                    ? null : service.getWindow().getWindow();
            if (window == null) {
                return;
            }

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(service);
            boolean enabled = preferences.getBoolean(PREFERENCE_KEY, false);
            if (!enabled || Build.VERSION.SDK_INT < 31 || !isBlurAvailable(service)) {
                restore(window, service);
                return;
            }

            synchronized (STATES) {
                if (!STATES.containsKey(window)) {
                    STATES.put(window, new WindowState(
                            getBackgroundBlurRadius(window),
                            window.getDecorView().getBackground(),
                            getRootBackground(service)));
                }
            }

            int radius = Math.round(BLUR_RADIUS_DP
                    * service.getResources().getDisplayMetrics().density);
            window.getClass().getMethod("setBackgroundBlurRadius", int.class)
                    .invoke(window, radius);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            setRootBackground(service, null);
        } catch (Throwable ignored) {
            // A platform-specific blur failure should not stop the keyboard from working.
        }
    }

    private static boolean isBlurAvailable(Context context) {
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
            return (Integer) window.getClass().getMethod("getBackgroundBlurRadius")
                    .invoke(window);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Drawable getRootBackground(InputMethodService service) {
        View root = getRootView(service);
        return root == null ? null : root.getBackground();
    }

    private static void setRootBackground(InputMethodService service, Drawable background) {
        View root = getRootView(service);
        if (root != null) {
            root.setBackground(background);
            if (background == null) {
                root.setBackgroundColor(Color.TRANSPARENT);
            }
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

    private static void restore(Window window, InputMethodService service) {
        WindowState state;
        synchronized (STATES) {
            state = STATES.remove(window);
        }
        if (state == null) {
            return;
        }
        try {
            window.getClass().getMethod("setBackgroundBlurRadius", int.class)
                    .invoke(window, state.blurRadius);
            window.setBackgroundDrawable(state.windowBackground);
            setRootBackground(service, state.rootBackground);
        } catch (Throwable ignored) {
            // Ignore unsupported window restoration APIs.
        }
        // The keyboard view belongs to the IME service; it is restored on the next invocation
        // if blur is re-enabled. The window is the important part of this teardown path.
    }

    private static final class WindowState {
        final int blurRadius;
        final Drawable windowBackground;
        final Drawable rootBackground;

        WindowState(int blurRadius, Drawable windowBackground, Drawable rootBackground) {
            this.blurRadius = blurRadius;
            this.windowBackground = windowBackground;
            this.rootBackground = rootBackground;
        }
    }
}
