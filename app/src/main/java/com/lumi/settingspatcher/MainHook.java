package com.lumi.settingspatcher;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Unhides Home and travel mode. Hides Meta AI. Stops VrShell from turning passthrough back on if disabled. */

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "SettingsPatcher";
    private static final String SETTINGS_PACKAGE = "com.oculus.panelapp.settings";
    private static final String VRSHELL_PACKAGE = "com.oculus.vrshell";

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            SETTINGS_PACKAGE,
            VRSHELL_PACKAGE,
            "com.oculus.systemux"
    ));

    private static final String PREF_MANAGER_CLASS = "com.oculus.os.PreferencesManager";
    private static final String FORCE_INT_ONE_PREF = "project_dubai_opted_in";

    private static final String META_AI_ENABLED_PREF = "mr_meta_ai_assistant_enabled";
    private static final String META_AI_NAV_URI = "/voice_commands";
    private static final String HOME_URI = "/home";

    private static final int V81_VERSION_CODE = 665903155;
    private static final String V81_TRAVEL_MODE_URI = "/travel_mode";
    private static final String V81_SECTION_UTIL_CLASS =
            "com.oculus.panelapp.settings.sections.util.SettingsSectionUtil";
    private static final String V81_NAV_UTIL_CLASS =
            "com.oculus.panelapp.settings.sections.util.SettingsNavigationUtil";
    private static final String V81_SIDE_NAV_ITEM_CLASS =
            "com.oculus.panelapp.settings.sections.util.SideNavMenuItemId";
    private static final String V81_OCSIDE_NAV_ITEM_CLASS = "com.oculus.ocui.OCSideNavMenuItem";

    private static final String PASSTHROUGH_ON_DEMAND_PREF = "passthrough_on_demand_enabled";


    /** All names we need for one specific version of the Settings app. */
    private static final class VersionConfig {
        // --- Nav sidebar (the left menu) ---

        // Class and method that builds the entire nav row list at startup.
        // Hooked to remove the Meta AI row.
        final String navBuilderClass;
        final String navBuilderMethod;
        // Type of the builder method's second parameter — needed to pick the right overload.
        final String navBuilderParam2Type;
        // Wrapper object representing a single nav row.
        final String navWrapperClass;
        // Enum-like class holding all known route URIs as static fields.
        final String navDescriptorClass;
        // Field name on navDescriptorClass whose value is the Home route URI.
        // null on versions where the Home route doesn't exist at all.
        final String homeConstField;

        // --- Home unhide ---

        // Preferred approach: a method that explicitly adds the Home row to the nav list.
        // When non-null this is hooked directly. null means this version has no such adder.
        final String homeAdderMethod;
        // Fallback approach: a gate method that returns true/false per route URI.
        // Used when homeAdderMethod is null — the module forces it to return true for /home.
        final String routeGateClass;
        final String routeGateMethod;

        VersionConfig(String navBuilderClass, String navBuilderMethod, String navBuilderParam2Type,
                       String navWrapperClass, String navDescriptorClass,
                       String homeConstField,
                       String routeGateClass, String routeGateMethod,
                       String homeAdderMethod) {
            this.navBuilderClass = navBuilderClass;
            this.navBuilderMethod = navBuilderMethod;
            this.navBuilderParam2Type = navBuilderParam2Type;
            this.navWrapperClass = navWrapperClass;
            this.navDescriptorClass = navDescriptorClass;
            this.homeConstField = homeConstField;
            this.routeGateClass = routeGateClass;
            this.routeGateMethod = routeGateMethod;
            this.homeAdderMethod = homeAdderMethod;
        }
    }

    /** Stops VrShell from turning passthrough back on with a sensor update */
    private static class VrShellConfig {
        final String sensorClass;
        final String outerField;
        final String outerClass;
        final String prefsAccessor;

        VrShellConfig(String sensorClass, String outerField, String outerClass, String prefsAccessor) {
            this.sensorClass = sensorClass;
            this.outerField = outerField;
            this.outerClass = outerClass;
            this.prefsAccessor = prefsAccessor;
        }
    }

    private static final Map<Integer, VrShellConfig> VRSHELL_VERSION_CONFIGS = new HashMap<>();
    static {
        VRSHELL_VERSION_CONFIGS.put(949708223, new VrShellConfig(
                "com.oculus.vrshell.input.DoubleTapSensor$1", "this$0",
                "com.oculus.vrshell.input.DoubleTapSensor", "access$000")); // v203 Q2/Q3/Pro
        VRSHELL_VERSION_CONFIGS.put(996826886,  new VrShellConfig("X.06u", "A00", "X.06t", "A00")); // v204 Q2/Q3/Pro
        VRSHELL_VERSION_CONFIGS.put(1009732165, new VrShellConfig("X.07o", "A00", "X.07n", "A00")); // v205 Q2/Q3/3s/Pro
        VRSHELL_VERSION_CONFIGS.put(998228807,  new VrShellConfig("X.07o", "A00", "X.07n", "A00")); // v205 Q2
        VRSHELL_VERSION_CONFIGS.put(1026630909, new VrShellConfig("X.05N", "A00", "X.05M", "A00")); // v206 Q2/Q3/3s
        VRSHELL_VERSION_CONFIGS.put(1026370745, new VrShellConfig("X.05N", "A00", "X.05M", "A00")); // v206 Pro
        VRSHELL_VERSION_CONFIGS.put(1028758766, new VrShellConfig("X.08B", "A00", "X.08A", "A00")); // v207 Q2/Q3
        VRSHELL_VERSION_CONFIGS.put(1051347662, new VrShellConfig("X.08B", "A00", "X.08A", "A00")); // v207 Q3/Pro
        VRSHELL_VERSION_CONFIGS.put(1051657337, new VrShellConfig("X.08B", "A00", "X.08A", "A00")); // v207 Q2
        VRSHELL_VERSION_CONFIGS.put(1039989429, new VrShellConfig("X.08B", "A00", "X.08A", "A00")); // v207 3s
        VRSHELL_VERSION_CONFIGS.put(1042715439, new VrShellConfig("X.08B", "A00", "X.08A", "A00")); // v207 3s
        VRSHELL_VERSION_CONFIGS.put(1037766809, new VrShellConfig("X.08B", "A00", "X.08A", "A00")); // v207 Pro
        VRSHELL_VERSION_CONFIGS.put(1032932075, new VrShellConfig("X.08B", "A00", "X.08A", "A00")); // v207 Pro
    }

    private static final Map<Integer, VersionConfig> VERSION_CONFIGS = new HashMap<>();
    static {
        // v207 base (Q2/Q3 only)
        VersionConfig v207 = new VersionConfig(
                "X.0aG", "A00", "X.0b7",
                "X.0ZV", "X.0aH", "A0E",
                "X.0aL", "A06",
                null);
        VERSION_CONFIGS.put(675101053, v207); // Q2+Q3 v207

        // v207-latest + 3s/Pro v207 (shifted obfuscation)
        VersionConfig v207latest = new VersionConfig(
                "X.0aH", "A00", "X.0b8",
                "X.0ZW", "X.0aI", "A0E",
                "X.0aM", "A06",
                null);
        VERSION_CONFIGS.put(675101304, v207latest); // Q3/Pro v207
        VERSION_CONFIGS.put(675101311, v207latest); // Q2 v207
        VERSION_CONFIGS.put(675101221, v207latest); // 3s v207
        VERSION_CONFIGS.put(675101195, v207latest); // 3s v207
        VERSION_CONFIGS.put(675101172, v207latest); // Pro v207

        VersionConfig v206 = new VersionConfig(
                "X.0Nk", "A00", "X.10U",
                "X.0No", "X.0Nj", "A0E",
                "X.0MW", "A05",
                "A01");
        VERSION_CONFIGS.put(674401129, v206);
        VERSION_CONFIGS.put(674401131, v206);
        VERSION_CONFIGS.put(674401169, v206);

        VersionConfig v205 = new VersionConfig(
                "X.0UY", "A00", "X.0Wf",
                "X.0RH", "X.0UZ", "A0E",
                "X.0cH", "A04",
                null);
        VERSION_CONFIGS.put(673301462, v205);
        VERSION_CONFIGS.put(673301368, v205);

        VersionConfig v204 = new VersionConfig(
                "X.1qV", "A00", "X.10C",
                "X.0xS", "X.1qW", "A0E",
                "X.1qU", "A04",
                null);
        VERSION_CONFIGS.put(672201326, v204);

        VersionConfig v203pro = new VersionConfig(
                "X.08f", "A00", "X.0ok",
                "X.08i", "X.08g", null,
                null, null,
                null);
        VERSION_CONFIGS.put(671701119, v203pro);
        VERSION_CONFIGS.put(671701082, v203pro);
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }

        Log.i(TAG, "Loaded into " + lpparam.packageName + " (pid=" + android.os.Process.myPid() + ")");

        installPreferenceHooks(lpparam);
        installHorizonOsPreferenceHook(lpparam);

        if (!SETTINGS_PACKAGE.equals(lpparam.packageName)) {
            if (VRSHELL_PACKAGE.equals(lpparam.packageName)) {
                try {
                    installVrShellDoubleTapFix(lpparam);
                } catch (Throwable t) {
                    Log.e(TAG, "VRSHELL DOUBLE-TAP: install failed", t);
                }
            }
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context ctx = (Context) param.args[0];
                            try {
                                installVersionSpecificHooks(lpparam, ctx);
                            } catch (Throwable t) {
                                Log.e(TAG, "Version-specific hook installation failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook Application.attach(Context) - can't determine the installed "
                    + "version, so Home/Meta-AI fixes will NOT be applied this run.", t);
        }
    }

    private void installVersionSpecificHooks(final LoadPackageParam lpparam, Context ctx) {
        int versionCode;
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            versionCode = info.versionCode;
            Log.i(TAG, "VERSION: " + ctx.getPackageName() + " versionName=" + info.versionName
                    + " versionCode=" + versionCode);
        } catch (Throwable t) {
            Log.e(TAG, "VERSION: failed to read installed versionCode - Home/Meta-AI "
                    + "fixes will NOT be applied this run.", t);
            return;
        }

        if (versionCode == V81_VERSION_CODE) {
            Log.i(TAG, "VERSION: v81 - using readable-class-name hooks");
            installV81NavHooks(lpparam);
            return;
        }

        final VersionConfig cfg = VERSION_CONFIGS.get(versionCode);
        if (cfg == null) {
            Log.e(TAG, "VERSION: no hardcoded support for versionCode=" + versionCode + " yet. "
                    + "Home/Meta-AI fixes will NOT apply this run - trace this version's APK "
                    + "(see README.md) and add an entry to VERSION_CONFIGS to support it. "
                    + "The /travel_mode fix still works regardless, since it is not version-specific.");
            return;
        }
        Log.i(TAG, "VERSION: using hardcoded support for versionCode=" + versionCode);

        installNavBuilderHook(lpparam, cfg);

        if (cfg.homeConstField == null) {
            Log.i(TAG, "HOME: this version has no Home route at all (confirmed via static trace) - skipping");
        } else if (cfg.homeAdderMethod != null) {
            installHomeUnhideViaAdderHook(lpparam, cfg);
        } else {
            installHomeUnhideHook(lpparam, cfg);
        }
    }

    /** v81 specific hooks */
    private void installV81NavHooks(final LoadPackageParam lpparam) {

        try {
            XposedHelpers.findAndHookMethod(
                    V81_SECTION_UTIL_CLASS, lpparam.classLoader, "isSectionAccessible",
                    Context.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String uri = (String) param.args[1];
                            if (V81_TRAVEL_MODE_URI.equals(uri)) {
                                param.setResult(Boolean.TRUE);
                                Log.i(TAG, "V81: isSectionAccessible -> true for " + uri);
                            } else if (META_AI_NAV_URI.equals(uri)) {
                                Context ctx = (Context) param.args[0];
                                if (!isMetaAiToggleOn(ctx)) {
                                    param.setResult(Boolean.FALSE);
                                    Log.i(TAG, "V81: isSectionAccessible -> false for Meta AI (toggle off)");
                                }
                            }
                        }
                    });
            Log.i(TAG, "V81: hooked " + V81_SECTION_UTIL_CLASS + ".isSectionAccessible");
        } catch (Throwable t) {
            Log.e(TAG, "V81: failed to hook isSectionAccessible", t);
        }

        try {
            XposedHelpers.findAndHookMethod(
                    V81_NAV_UTIL_CLASS, lpparam.classLoader,
                    "generateNavItems$rvp0$0$uva1$0",
                    Context.class,
                    "com.oculus.horizoncontent.profile.SelfVRProfileContent",
                    "com.oculus.os.q4b.mma.MMAControls",
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Context ctx = (Context) param.args[0];
                                List<Object> list = new ArrayList<>((List<?>) param.getResult());

                                Class<?> sideNavItemIdClass = lpparam.classLoader.loadClass(V81_SIDE_NAV_ITEM_CLASS);
                                Object spaceSetupConst = sideNavItemIdClass.getDeclaredField("SPACE_SETUP_ID").get(null);
                                Field iconResIdField = null;
                                for (Field f : spaceSetupConst.getClass().getDeclaredFields()) {
                                    if (f.getType() == int.class) {
                                        f.setAccessible(true);
                                        iconResIdField = f;
                                        break;
                                    }
                                }
                                android.graphics.drawable.Drawable icon = null;
                                if (iconResIdField != null) {
                                    int iconResId = (int) iconResIdField.get(spaceSetupConst);
                                    icon = ctx.getDrawable(iconResId);
                                }
                                if (icon == null) {
                                    Log.e(TAG, "V81: couldn't load SPACE_SETUP icon for nav injection - skipping");
                                    return;
                                }

                                Class<?> menuItemClass = lpparam.classLoader.loadClass(V81_OCSIDE_NAV_ITEM_CLASS);

                                Class<Enum> iconSizeClass = null;
                                for (Class<?> inner : menuItemClass.getDeclaredClasses()) {
                                    if (inner.isEnum()) {
                                        iconSizeClass = (Class<Enum>) inner;
                                        break;
                                    }
                                }
                                if (iconSizeClass == null) {
                                    Log.e(TAG, "V81: couldn't find OCSideNavMenuItem$IconSize - skipping injection");
                                    return;
                                }
                                Object smallSize = Enum.valueOf(iconSizeClass, "SMALL");

                                java.lang.reflect.Constructor<?> ctor = null;
                                for (java.lang.reflect.Constructor<?> c : menuItemClass.getConstructors()) {
                                    Class<?>[] pt = c.getParameterTypes();
                                    if (pt.length == 8
                                            && pt[0] == android.graphics.drawable.Drawable.class
                                            && pt[3] == String.class
                                            && pt[4] == String.class
                                            && pt[5] == String.class
                                            && pt[6] == int.class
                                            && pt[7] == boolean.class) {
                                        ctor = c;
                                        break;
                                    }
                                }
                                if (ctor == null) {
                                    Log.e(TAG, "V81: couldn't find OCSideNavMenuItem 8-arg constructor - skipping injection");
                                    return;
                                }

                                Object travelModeItem = ctor.newInstance(
                                        icon, null, smallSize,
                                        "travel_mode_nav_item", "Travel Mode", V81_TRAVEL_MODE_URI, 0, false);

                                int insertIdx = -1;
                                for (int i = 0; i < list.size(); i++) {
                                    Object item = list.get(i);
                                    if (item == null || !menuItemClass.isInstance(item)) continue;
                                    for (Field f : menuItemClass.getDeclaredFields()) {
                                        if (f.getType() != String.class) continue;
                                        f.setAccessible(true);
                                        Object val = f.get(item);
                                        if ("/space_setup".equals(val)) {
                                            insertIdx = i + 1;
                                            break;
                                        }
                                    }
                                    if (insertIdx >= 0) break;
                                }
                                if (insertIdx < 0) {
                                    insertIdx = list.size();
                                    Log.i(TAG, "V81: SPACE_SETUP not found in nav list - appending to end");
                                }

                                list.add(insertIdx, travelModeItem);
                                param.setResult(list);
                                Log.i(TAG, "V81: injected /travel_mode nav item at index " + insertIdx
                                        + " (list now has " + list.size() + " items)");
                            } catch (Throwable t) {
                                Log.e(TAG, "V81: nav item injection failed", t);
                            }
                        }
                    });
            Log.i(TAG, "V81: hooked " + V81_NAV_UTIL_CLASS + ".generateNavItems");
        } catch (Throwable t) {
            Log.e(TAG, "V81: failed to hook generateNavItems", t);
        }
    }

    /** Forces /travel_mode on by making the preferences manager always return 1 */
    private void installPreferenceHooks(LoadPackageParam lpparam) {
        try {
            Class<?> prefManagerClass = lpparam.classLoader.loadClass(PREF_MANAGER_CLASS);
            XposedHelpers.findAndHookMethod(prefManagerClass, "getInteger", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            if (FORCE_INT_ONE_PREF.equals(key)) {
                                param.setResult(new android.util.Pair<>(Boolean.TRUE, Integer.valueOf(1)));
                            }
                        }
                    });
            Log.i(TAG, "PREF: hooked " + PREF_MANAGER_CLASS + ".getInteger(String)");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook " + PREF_MANAGER_CLASS + " - /travel_mode won't be forced this run.", t);
        }
    }

    /** Quick Settings Travel Mode tile. **/
    private void installHorizonOsPreferenceHook(LoadPackageParam lpparam) {
        try {
            Class<?> cls = lpparam.classLoader.loadClass("horizonos.os.preferences.PreferencesManager");
            int n = 0;
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("getInt")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 1 || pt[0] != String.class || m.getReturnType() != int.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (FORCE_INT_ONE_PREF.equals(param.args[0])) {
                            param.setResult(Integer.valueOf(1));
                        }
                    }
                });
                n++;
            }
            Log.i(TAG, "PREF: hooked horizonos.os.preferences.PreferencesManager.getInt(String) x" + n
                    + " in " + lpparam.packageName + " (Quick Settings Travel Mode tile)");
        } catch (ClassNotFoundException e) {
            Log.i(TAG, "PREF: horizonos.os.preferences.PreferencesManager not present in "
                    + lpparam.packageName + " - skipping Quick Settings tile hook");
        } catch (Throwable t) {
            Log.e(TAG, "PREF: failed to hook horizonos PreferencesManager - Quick Settings "
                    + "Travel Mode tile won't be forced this run.", t);
        }
    }

    /** Removes Meta AI nav item if its toggle is off */
    private void installNavBuilderHook(final LoadPackageParam lpparam, final VersionConfig cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    cfg.navBuilderClass, lpparam.classLoader, cfg.navBuilderMethod,
                    Context.class, cfg.navBuilderParam2Type, "com.oculus.os.q4b.mma.MMAControls",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object result = param.getResult();
                                if (!(result instanceof List)) return;
                                Context ctx = (Context) param.args[0];

                                @SuppressWarnings("unchecked")
                                List<Object> original = (List<Object>) result;
                                ArrayList<Object> list = new ArrayList<>(original);
                                boolean changed = false;

                                boolean metaAiOn = isMetaAiToggleOn(ctx);
                                Iterator<Object> it = list.iterator();
                                while (it.hasNext()) {
                                    Object item = it.next();
                                    Object descriptor = readWrapperDescriptor(item, cfg.navWrapperClass);
                                    if (descriptor == null) continue;
                                    String uri = readStringField(descriptor, "uri");
                                    if (!metaAiOn && META_AI_NAV_URI.equals(uri)) {
                                        it.remove();
                                        changed = true;
                                    }
                                }

                                if (changed) {
                                    param.setResult(list);
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "NAVLIST: " + cfg.navBuilderClass + "." + cfg.navBuilderMethod
                                        + " post-filter hook body failed", t);
                            }
                        }
                    });
            Log.i(TAG, "NAVLIST: hooked " + cfg.navBuilderClass + "." + cfg.navBuilderMethod);
        } catch (Throwable t) {
            Log.e(TAG, "NAVLIST: failed to hook " + cfg.navBuilderClass + "." + cfg.navBuilderMethod
                    + " - wrong for this versionCode's VERSION_CONFIGS entry?", t);
        }
    }

    private static Object readWrapperDescriptor(Object item, String wrapperClassName) {
        if (item == null || !wrapperClassName.equals(item.getClass().getName())) return null;
        try {
            return item.getClass().getField("A00").get(item);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readStringField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Object v = obj.getClass().getField(fieldName).get(obj);
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Checks the Meta AI toggle through the system preferences service */
    private static boolean isMetaAiToggleOn(Context ctx) {
        try {
            Class<?> prefMgrClass = ctx.getClassLoader().loadClass("horizonos.os.preferences.PreferencesManager");
            Object prefMgr = Context.class.getMethod("getSystemService", Class.class).invoke(ctx, prefMgrClass);
            Object result = prefMgrClass.getMethod("getBoolean", String.class).invoke(prefMgr, META_AI_ENABLED_PREF);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            Log.e(TAG, "META-AI: failed to read '" + META_AI_ENABLED_PREF
                    + "' - defaulting to NOT hiding the row (fail safe)", t);
            return true;
        }
    }

    /** Unhides Home section (direct adder variant) */
    private void installHomeUnhideViaAdderHook(final LoadPackageParam lpparam, final VersionConfig cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    cfg.navBuilderClass, lpparam.classLoader, cfg.homeAdderMethod,
                    Context.class, cfg.navDescriptorClass, java.util.AbstractCollection.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object enumConst = param.args[1];
                            if (!HOME_URI.equals(readStringField(enumConst, "uri"))) {
                                return;
                            }
                            try {
                                Class<?> wrapperClass = lpparam.classLoader.loadClass(cfg.navWrapperClass);
                                Object row = XposedHelpers.newInstance(wrapperClass, enumConst);
                                ((java.util.AbstractCollection) param.args[2]).add(row);
                            } catch (Throwable t) {
                                Log.e(TAG, "HOME: failed to manually add the Home row", t);
                            }
                            param.setResult(null);
                        }
                    });
            Log.i(TAG, "HOME: hooked " + cfg.navBuilderClass + "." + cfg.homeAdderMethod + " (direct adder)");
        } catch (Throwable t) {
            Log.e(TAG, "HOME: failed to hook " + cfg.navBuilderClass + "." + cfg.homeAdderMethod
                    + " - wrong for this versionCode's VERSION_CONFIGS entry?", t);
        }
    }

    /** Unhides Home section (gate fallback variant) */
    private void installHomeUnhideHook(LoadPackageParam lpparam, VersionConfig cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    cfg.routeGateClass, lpparam.classLoader, cfg.routeGateMethod,
                    Context.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (HOME_URI.equals(param.args[1])) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    });
            Log.i(TAG, "HOME: hooked " + cfg.routeGateClass + "." + cfg.routeGateMethod);
        } catch (Throwable t) {
            Log.e(TAG, "HOME: failed to hook " + cfg.routeGateClass + "." + cfg.routeGateMethod
                    + " - wrong for this versionCode's VERSION_CONFIGS entry?", t);
        }
    }

    /** Entry point for the VrShell Double Tap Fix */
    private void installVrShellDoubleTapFix(final LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context ctx = (Context) param.args[0];
                            installVrShellDoubleTapFixForVersion(lpparam, ctx);
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "VRSHELL DOUBLE-TAP: failed to hook Application.attach(Context) - can't "
                    + "determine the installed versionCode, so this fix will NOT be applied this run.", t);
        }
    }

    /** Reads VrShell's version and hooks the sensor method */
    private void installVrShellDoubleTapFixForVersion(final LoadPackageParam lpparam, Context ctx) {
        int versionCode;
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            versionCode = info.versionCode;
            Log.i(TAG, "VRSHELL VERSION: " + ctx.getPackageName() + " versionName=" + info.versionName
                    + " versionCode=" + versionCode);
        } catch (Throwable t) {
            Log.e(TAG, "VRSHELL VERSION: failed to read installed versionCode - double-tap fix will "
                    + "NOT be applied this run.", t);
            return;
        }

        final VrShellConfig cfg = VRSHELL_VERSION_CONFIGS.get(versionCode);
        if (cfg == null) {
            Log.e(TAG, "VRSHELL VERSION: no hardcoded support for versionCode=" + versionCode + " yet. "
                    + "Trace this version's VrShell.apk and add an entry to VRSHELL_VERSION_CONFIGS to support it.");
            return;
        }

        try {
            final Class<?> outerClass = lpparam.classLoader.loadClass(cfg.outerClass);
            XposedHelpers.findAndHookMethod(
                    cfg.sensorClass, lpparam.classLoader, "onSensorChanged",
                    "android.hardware.SensorEvent",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object outer = XposedHelpers.getObjectField(param.thisObject, cfg.outerField);
                                Object prefs = XposedHelpers.callStaticMethod(outerClass, cfg.prefsAccessor, outer);
                                boolean currentlyOn = (Boolean) XposedHelpers.callMethod(
                                        prefs, "getBoolean", PASSTHROUGH_ON_DEMAND_PREF);
                                if (!currentlyOn) {
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "VRSHELL DOUBLE-TAP: failed to read current toggle state - "
                                        + "letting the original onSensorChanged run this time", t);
                            }
                        }
                    });
            Log.i(TAG, "VRSHELL DOUBLE-TAP: hooked " + cfg.sensorClass + ".onSensorChanged"
                    + " - now skips the whole method while " + PASSTHROUGH_ON_DEMAND_PREF
                    + " reads false, which blocks the forced re-enable directly");
        } catch (Throwable t) {
            Log.e(TAG, "VRSHELL DOUBLE-TAP: failed to install fix for " + cfg.sensorClass + ".onSensorChanged"
                    + " - wrong for this versionCode's VRSHELL_VERSION_CONFIGS entry?", t);
        }
    }

}
