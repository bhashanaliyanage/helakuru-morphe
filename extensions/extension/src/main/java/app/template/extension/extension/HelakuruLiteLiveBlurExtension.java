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
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Runtime blur and tint behavior for the Lite Frosted Glass settings. */
public final class HelakuruLiteLiveBlurExtension {
    private static final String ENABLED = "morphe_live_keyboard_blur_lite";
    private static final String BLUR_STRENGTH = "morphe_frosted_blur_strength";
    private static final String TRANSPARENCY_FOLLOWS_THEME = "morphe_frosted_transparency_follow_theme";
    private static final String TRANSPARENCY_AMOUNT = "morphe_frosted_transparency_amount";
    private static final String COLOR_THEME = "morphe_frosted_color_theme";
    private static final int DEFAULT_BLUR_DP = 32;
    private static final int DEFAULT_TRANSPARENCY = 50;
    private static final Map<Window, WindowState> STATES = new WeakHashMap<>();

    private HelakuruLiteLiveBlurExtension() { }

    /** Called at the end of Lite's input-view setup, after it has applied the active theme. */
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
            WindowState state;
            synchronized (STATES) {
                state = STATES.get(window);
                if (state == null) {
                    state = new WindowState(getBackgroundBlurRadius(window),
                            window.getDecorView().getBackground());
                    STATES.put(window, state);
                }
            }
            if (Build.VERSION.SDK_INT >= 31 && isBlurAvailable(service)) {
                int blurDp = clamp(preferences.getInt(BLUR_STRENGTH, DEFAULT_BLUR_DP), 0, 80);
                int radius = Math.round(blurDp * service.getResources().getDisplayMetrics().density);
                window.getClass().getMethod("setBackgroundBlurRadius", int.class).invoke(window, radius);
            }
            window.setBackgroundDrawableResource(android.R.color.transparent);
            applyKeyboardSurfaces(service, root, state, preferences);
            installRefreshListener(service, root, state);
        } catch (Throwable ignored) {
            // Keep the IME usable if a platform or Lite internal changes.
        }
    }

    private static void applyKeyboardSurfaces(InputMethodService service, View root,
                                              WindowState state, SharedPreferences preferences) {
        if (root == null) return;
        int themeSurface = getHelakuruBackgroundColor(root);
        int tint = getTintColor(service, root, preferences);
        int transparency = getTransparency(service, themeSurface);
        int alpha = Math.round((100 - transparency) * 2.55f);
        ColorDrawable rootTint = new ColorDrawable((tint & 0x00ffffff) | (alpha << 24));
        applyBackground(root, state, rootTint);

        // KeyboardView has a stock opaque background of its own. Let the root's
        // tinted surface show through instead of leaving the keys on Lite's white fill.
        View keyboard = asView(getFieldValue(service, "keyboardView"));
        if (keyboard == null) {
            int keyboardId = service.getResources().getIdentifier("keyboard_view", "id", service.getPackageName());
            if (keyboardId != 0) keyboard = root.findViewById(keyboardId);
        }
        if (keyboard != null) applyBackground(keyboard, state, transparentDrawable());

        Object candidateViews = getFieldValue(service, "candidateViews");
        Object predictionColor = getFieldValue(candidateViews, "predictionBarColor");
        View toolbar = asView(getFieldValue(candidateViews, "container"));
        if (toolbar == null) toolbar = findCandidateContainer(service, root);
        if (toolbar != null) {
            // The toolbar backdrop is a separate Lite surface; clear it so it
            // inherits the same root tint and alpha as the key area.
            applyBackground(toolbar, state, transparentDrawable());
            int toolbarThemeColor = predictionColor instanceof Integer
                    ? (Integer) predictionColor : themeSurface;
            clearToolbarSurfaces(toolbar, state, toolbarThemeColor);
        }
    }

    private static void clearToolbarSurfaces(View view, WindowState state, int themeColor) {
        Drawable background = view.getBackground();
        // Lite uses flat theme-colored backgrounds for every unselected toolbar
        // button as well as its backing layers. Rounded selection drawables and
        // ImageView foreground icons must remain untouched.
        if (background instanceof ColorDrawable
                && ((ColorDrawable) background).getColor() == themeColor) {
            SurfaceState surface = state.surfaces.get(view);
            if (surface != null && background == surface.applied) {
                // setBackgroundColor can mutate our drawable in place on a theme
                // refresh. Save the new stock color before clearing it again.
                surface.original = new ColorDrawable(themeColor);
            }
            applyBackground(view, state, transparentDrawable());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearToolbarSurfaces(group.getChildAt(i), state, themeColor);
            }
        }
    }

    private static void applyBackground(View view, WindowState state, Drawable target) {
        if (view == null) return;
        Drawable current = view.getBackground();
        SurfaceState surface = state.surfaces.get(view);
        if (surface == null) {
            surface = new SurfaceState(current);
            state.surfaces.put(view, surface);
        } else if (current != surface.applied) {
            // Lite may replace a theme background during a keyboard/theme refresh.
            surface.original = current;
        } else if (current instanceof ColorDrawable && target instanceof ColorDrawable) {
            // Reuse our drawable on every pre-draw; replacing it continuously would
            // invalidate the keyboard every frame and could lose its saved original.
            ((ColorDrawable) current).setColor(((ColorDrawable) target).getColor());
            surface.applied = current;
            return;
        }
        view.setBackground(target);
        surface.applied = target;
    }

    private static void installRefreshListener(InputMethodService service, View root, WindowState state) {
        if (state.listenerRoot == root) return;
        removeRefreshListener(state);
        state.listenerRoot = root;
        state.listener = () -> {
            try {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(service);
                if (!preferences.getBoolean(ENABLED, false)) {
                    Window window = service.getWindow() == null ? null : service.getWindow().getWindow();
                    if (window != null) restore(window, service);
                } else {
                    applyKeyboardSurfaces(service, getRootView(service), state, preferences);
                }
            } catch (Throwable ignored) { }
            return true;
        };
        root.getViewTreeObserver().addOnPreDrawListener(state.listener);
    }

    private static void removeRefreshListener(WindowState state) {
        if (state.listener != null && state.listenerRoot != null) {
            ViewTreeObserver observer = state.listenerRoot.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(state.listener);
        }
        state.listener = null;
        state.listenerRoot = null;
    }

    private static int getTintColor(Context context, View root, SharedPreferences preferences) {
        if ("monet".equals(preferences.getString(COLOR_THEME, "monet"))) {
            Integer color = HelakuruLiteAccentChooser.resolve(context,
                    preferences.getString(HelakuruLiteAccentChooser.PREFERENCE_KEY,
                            HelakuruLiteAccentChooser.DEFAULT_ACCENT));
            if (color != null) return color;
        }
        return getHelakuruBackgroundColor(root);
    }

    private static int getTransparency(Context context, int themeColor) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.getBoolean(TRANSPARENCY_FOLLOWS_THEME, true)) {
            return clamp(preferences.getInt(TRANSPARENCY_AMOUNT, DEFAULT_TRANSPARENCY), 0, 100);
        }
        double luminance = (0.2126 * Color.red(themeColor) + 0.7152 * Color.green(themeColor)
                + 0.0722 * Color.blue(themeColor)) / 255.0;
        return luminance < 0.45 ? 82 : 90;
    }

    private static int getHelakuruBackgroundColor(View root) {
        try {
            Object theme = getFieldValue(root, "theme");
            Field surface = theme.getClass().getField("f");
            return surface.getInt(theme);
        } catch (Throwable ignored) { }
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
        return asView(getFieldValue(service, "rootView"));
    }

    private static View findCandidateContainer(Context context, View root) {
        int id = context.getResources().getIdentifier("candidate_container", "id", context.getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }

    private static Object getFieldValue(Object object, String name) {
        if (object == null) return null;
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View asView(Object object) {
        return object instanceof View ? (View) object : null;
    }

    private static ColorDrawable transparentDrawable() {
        return new ColorDrawable(Color.TRANSPARENT);
    }

    private static void restore(Window window, InputMethodService service) {
        WindowState state;
        synchronized (STATES) {
            state = STATES.remove(window);
        }
        if (state == null) return;
        removeRefreshListener(state);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                window.getClass().getMethod("setBackgroundBlurRadius", int.class)
                        .invoke(window, state.blurRadius);
            }
            window.setBackgroundDrawable(state.windowBackground);
            for (Map.Entry<View, SurfaceState> entry : new ArrayList<>(state.surfaces.entrySet())) {
                View view = entry.getKey();
                SurfaceState surface = entry.getValue();
                if (view != null && view.getBackground() == surface.applied) {
                    view.setBackground(surface.original);
                }
            }
        } catch (Throwable ignored) {
            // Ignore unsupported restoration APIs.
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SurfaceState {
        Drawable original;
        Drawable applied;
        SurfaceState(Drawable original) { this.original = original; }
    }

    private static final class WindowState {
        final int blurRadius;
        final Drawable windowBackground;
        final Map<View, SurfaceState> surfaces = new IdentityHashMap<>();
        View listenerRoot;
        ViewTreeObserver.OnPreDrawListener listener;
        WindowState(int blurRadius, Drawable windowBackground) {
            this.blurRadius = blurRadius;
            this.windowBackground = windowBackground;
        }
    }
}
