package app.template.extension.extension;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;

/** Runtime code used by the Morphe patch to expose and apply Helakuru's blur setting. */
public final class LiveBlurExtension {
    private static final String PREFERENCE_KEY = "morphe_live_keyboard_blur";
    private static final int BLUR_RADIUS_DP = 32;

    private static final Map<View, ViewState> VIEW_STATES = new WeakHashMap<>();
    private static final Map<Window, WindowState> WINDOW_STATES = new WeakHashMap<>();

    private LiveBlurExtension() {
    }

    /** Connect the added SwitchPreference to Helakuru's process-shared MMKV store. */
    public static void bindSettings(Object fragmentObject) {
        try {
            final Context context = (Context) fragmentObject.getClass()
                    .getMethod("requireContext")
                    .invoke(fragmentObject);
            Object preference = fragmentObject.getClass()
                    .getMethod("findPreference", String.class)
                    .invoke(fragmentObject, PREFERENCE_KEY);
            if (preference == null) {
                return;
            }

            boolean enabled = readPreference(context);
            preference.getClass().getMethod("setChecked", boolean.class)
                    .invoke(preference, enabled);

            Class<?> listenerClass = Class.forName(
                    "androidx.preference.Preference$OnPreferenceChangeListener");
            InvocationHandler handler = (proxy, method, args) -> {
                if ("onPreferenceChange".equals(method.getName())
                        && args != null && args.length == 2 && args[1] instanceof Boolean) {
                    writePreference(context, (Boolean) args[1]);
                    return true;
                }
                return null;
            };
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(), new Class<?>[]{listenerClass}, handler);
            preference.getClass()
                    .getMethod("setOnPreferenceChangeListener", listenerClass)
                    .invoke(preference, listener);
        } catch (Throwable ignored) {
            // A settings binding issue should not prevent Helakuru's settings screen opening.
        }
    }

    /** Apply Android's IME-specific blur-behind effect while the keyboard is visible. */
    public static void applyKeyboardBlur(Object serviceObject) {
        if (!(serviceObject instanceof InputMethodService)) {
            return;
        }
        InputMethodService service = (InputMethodService) serviceObject;
        try {
            Window dialogWindow = service.getWindow() == null
                    ? null : service.getWindow().getWindow();
            if (dialogWindow == null) {
                return;
            }

            boolean enabled = readPreference(service);
            if (Build.VERSION.SDK_INT < 31 || (enabled && !isCrossWindowBlurEnabled(service))) {
                restore(dialogWindow);
                return;
            }

            if (enabled) {
                enable(dialogWindow, service);
            } else {
                restore(dialogWindow);
            }
        } catch (Throwable ignored) {
            // Preserve normal keyboard operation if this Android build rejects window blur.
        }
    }

    private static boolean readPreference(Context context) throws Exception {
        Class<?> utils = Class.forName("lk.bhasha.helakuru.util.Utils");
        Object preferences = utils.getMethod("getSharedPreference", Context.class)
                .invoke(null, context);
        return (Boolean) preferences.getClass()
                .getMethod("getBoolean", String.class, boolean.class)
                .invoke(preferences, PREFERENCE_KEY, false);
    }

    private static void writePreference(Context context, boolean enabled) throws Exception {
        Class<?> utils = Class.forName("lk.bhasha.helakuru.util.Utils");
        Object preferences = utils.getMethod("getSharedPreference", Context.class)
                .invoke(null, context);
        Object updated = preferences.getClass()
                .getMethod("putBoolean", String.class, boolean.class)
                .invoke(preferences, PREFERENCE_KEY, enabled);
        updated.getClass().getMethod("apply").invoke(updated);
    }

    private static boolean isCrossWindowBlurEnabled(Context context) {
        try {
            Object manager = context.getSystemService(Context.WINDOW_SERVICE);
            return (Boolean) manager.getClass()
                    .getMethod("isCrossWindowBlurEnabled")
                    .invoke(manager);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void enable(Window window, InputMethodService service) throws Exception {
        WindowManager.LayoutParams attributes = window.getAttributes();
        synchronized (WINDOW_STATES) {
            if (!WINDOW_STATES.containsKey(window)) {
                int oldRadius = (Integer) attributes.getClass()
                        .getMethod("getBlurBehindRadius")
                        .invoke(attributes);
                WINDOW_STATES.put(window, new WindowState(
                        (attributes.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0,
                        oldRadius,
                        window.getDecorView().getBackground()));
            }
        }

        attributes.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        int radius = Math.round(BLUR_RADIUS_DP
                * service.getResources().getDisplayMetrics().density);
        attributes.getClass().getMethod("setBlurBehindRadius", int.class)
                .invoke(attributes, radius);
        window.setAttributes(attributes);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        makeKeyboardBackdropTransparent(service);
    }

    private static void makeKeyboardBackdropTransparent(InputMethodService service) {
        View root;
        try {
            // In Helakuru 12.2.13, field A holds the current input view returned by
            // onCreateInputView(). InputMethodService does not expose a public getter.
            Field inputView = service.getClass().getField("A");
            root = (View) inputView.get(service);
        } catch (Throwable ignored) {
            return;
        }
        if (root == null) {
            return;
        }

        setTransparent(root);
        addNamedView(service, root, "layout_keyboard_parent", true);
        addNamedView(service, root, "layout_candidate_background", true);
        addNamedView(service, root, "tv_bg_right", true);
        addNamedView(service, root, "extra_emoji_row", true);

        // Helakuru inserts its theme wallpaper image as the first child of the keyboard root.
        if (root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0) {
            View first = ((ViewGroup) root).getChildAt(0);
            if (first instanceof android.widget.ImageView) {
                saveState(first);
                first.setVisibility(View.INVISIBLE);
            }
        }
    }

    private static void addNamedView(
            InputMethodService service, View root, String name, boolean clearBackground) {
        int id = service.getResources().getIdentifier(name, "id", service.getPackageName());
        if (id == 0) {
            return;
        }
        View view = root.findViewById(id);
        if (view != null && clearBackground) {
            setTransparent(view);
        }
    }

    private static void setTransparent(View view) {
        saveState(view);
        view.setBackgroundColor(Color.TRANSPARENT);
    }

    private static void saveState(View view) {
        synchronized (VIEW_STATES) {
            if (!VIEW_STATES.containsKey(view)) {
                VIEW_STATES.put(view, new ViewState(
                        view.getBackground(), view.getAlpha(), view.getVisibility()));
            }
        }
    }

    private static void restore(Window window) {
        WindowState oldWindowState;
        synchronized (WINDOW_STATES) {
            oldWindowState = WINDOW_STATES.remove(window);
        }
        if (oldWindowState != null) {
            try {
                WindowManager.LayoutParams attributes = window.getAttributes();
                if (oldWindowState.hadBlurFlag) {
                    attributes.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                } else {
                    attributes.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                }
                attributes.getClass().getMethod("setBlurBehindRadius", int.class)
                        .invoke(attributes, oldWindowState.blurRadius);
                window.setAttributes(attributes);
                window.setBackgroundDrawable(oldWindowState.background);
            } catch (Throwable ignored) {
                // Window attributes vary across Android builds and keyboard implementations.
            }
        }

        synchronized (VIEW_STATES) {
            for (Map.Entry<View, ViewState> entry : VIEW_STATES.entrySet()) {
                View view = entry.getKey();
                ViewState state = entry.getValue();
                if (view != null) {
                    view.setBackground(state.background);
                    view.setAlpha(state.alpha);
                    view.setVisibility(state.visibility);
                }
            }
            VIEW_STATES.clear();
        }
    }

    private static final class ViewState {
        final Drawable background;
        final float alpha;
        final int visibility;

        ViewState(Drawable background, float alpha, int visibility) {
            this.background = background;
            this.alpha = alpha;
            this.visibility = visibility;
        }
    }

    private static final class WindowState {
        final boolean hadBlurFlag;
        final int blurRadius;
        final Drawable background;

        WindowState(boolean hadBlurFlag, int blurRadius, Drawable background) {
            this.hadBlurFlag = hadBlurFlag;
            this.blurRadius = blurRadius;
            this.background = background;
        }
    }
}
