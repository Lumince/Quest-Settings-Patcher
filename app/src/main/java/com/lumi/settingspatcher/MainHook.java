package com.lumi.settingspatcher;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
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

/** Unhides Home, Passthrough, travel mode. Hides Meta AI and stops VrShell from turning passthrough back on if its disabled */

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "SettingsPatcher";
    private static final String SETTINGS_PACKAGE = "com.oculus.panelapp.settings";
    private static final String VRSHELL_PACKAGE = "com.oculus.vrshell";

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            SETTINGS_PACKAGE,
            VRSHELL_PACKAGE,
            "com.oculus.systemux"
    ));

    /** Only scan for these classes */
    private static final String[] SCAN_PACKAGE_PREFIXES = {
            "X.",
            "com.oculus.",
            "com.facebook.",
            "horizonos.",
    };

    /** Methods we need to scan for */
    private static final Set<String> FORCE_ENABLED_KEYS = new HashSet<>(Arrays.asList(
            "double_tap_passthrough:is_enabled"
    ));

    private static final String PREF_MANAGER_CLASS = "com.oculus.os.PreferencesManager";
    private static final String FORCE_INT_ONE_PREF = "project_dubai_opted_in";

    private static final String META_AI_ENABLED_PREF = "mr_meta_ai_assistant_enabled";
    private static final String META_AI_NAV_URI = "/voice_commands";
    private static final String HOME_URI = "/home";
    private static final String PASSTHROUGH_URI = "/passthrough_setup";
    private static final String PASSTHROUGH_TITLE = "Passthrough";

    private static final int V81_VERSION_CODE = 665903155;
    private static final String V81_PASSTHROUGH_URI = "/passthrough";
    private static final String V81_TRAVEL_MODE_URI = "/travel_mode";
    private static final String V81_SECTION_UTIL_CLASS =
            "com.oculus.panelapp.settings.sections.util.SettingsSectionUtil";
    private static final String V81_NAV_UTIL_CLASS =
            "com.oculus.panelapp.settings.sections.util.SettingsNavigationUtil";
    private static final String V81_SIDE_NAV_ITEM_CLASS =
            "com.oculus.panelapp.settings.sections.util.SideNavMenuItemId";
    private static final String V81_OCSIDE_NAV_ITEM_CLASS = "com.oculus.ocui.OCSideNavMenuItem";

   /** All names we need for one specific version of the Settings app. */
    private static final class VersionConfig {
        // --- Nav sidebar (the left menu) ---
 
        // Class and method that builds the entire nav row list at startup.
        // Hooked to inject the Passthrough row and remove the Meta AI row.
        final String navBuilderClass;
        final String navBuilderMethod;
        // Type of the builder method's second parameter — needed to pick the right overload.
        final String navBuilderParam2Type;
        // Wrapper object representing a single nav row. The module creates one to inject Passthrough.
        final String navWrapperClass;
        // Enum-like class holding all known route URIs as static fields (Home, Passthrough, etc.).
        final String navDescriptorClass;
        // Field name on navDescriptorClass whose value is the Passthrough route URI.
        final String passthroughConstField;
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
 
        // --- Passthrough page content ---
 
        // Base class that every settings page extends. Has a row-getter that silently
        // filters out the Passthrough row — overridden on the Passthrough page instance only.
        final String pageSectionClass;
        // The row-getter method on pageSectionClass that does the filtering.
        final String pageGetterMethod;
        // Method that adds rows to a page's list. No longer actively used but kept for
        // reference when tracing new versions.
        final String pageAdderMethod;
        // The specific subclass that is the Passthrough settings page.
        // The getter override is applied only to instances of this class.
        final String passthroughPageClass;
        // The real unfiltered row list lives two field-hops deep on the page instance.
        // backingFieldOuter is the field on the page; backingFieldInner is the field on
        // whatever that points to, where the actual List lives.
        final String backingFieldOuter;
        final String backingFieldInner;
        // Secondary class/method that keeps the page's displayed state in sync.
        // null when not yet traced for this version.
        final String pageSyncClass;
        final String pageSyncMethod;
 
        VersionConfig(String navBuilderClass, String navBuilderMethod, String navBuilderParam2Type,
                       String navWrapperClass, String navDescriptorClass,
                       String passthroughConstField, String homeConstField,
                       String routeGateClass, String routeGateMethod,
                       String pageSectionClass, String pageGetterMethod, String pageAdderMethod,
                       String passthroughPageClass,
                       String backingFieldOuter, String backingFieldInner,
                       String homeAdderMethod,
                       String pageSyncClass, String pageSyncMethod) {
            this.navBuilderClass = navBuilderClass;
            this.navBuilderMethod = navBuilderMethod;
            this.navBuilderParam2Type = navBuilderParam2Type;
            this.navWrapperClass = navWrapperClass;
            this.navDescriptorClass = navDescriptorClass;
            this.passthroughConstField = passthroughConstField;
            this.homeConstField = homeConstField;
            this.routeGateClass = routeGateClass;
            this.routeGateMethod = routeGateMethod;
            this.pageSectionClass = pageSectionClass;
            this.pageGetterMethod = pageGetterMethod;
            this.pageAdderMethod = pageAdderMethod;
            this.passthroughPageClass = passthroughPageClass;
            this.backingFieldOuter = backingFieldOuter;
            this.backingFieldInner = backingFieldInner;
            this.homeAdderMethod = homeAdderMethod;
            this.pageSyncClass = pageSyncClass;
            this.pageSyncMethod = pageSyncMethod;
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
                "com.oculus.vrshell.input.DoubleTapSensor", "access$000"));
        VRSHELL_VERSION_CONFIGS.put(996826886, new VrShellConfig("X.06u", "A00", "X.06t", "A00"));
        VRSHELL_VERSION_CONFIGS.put(1009732165, new VrShellConfig("X.07o", "A00", "X.07n", "A00"));
        VRSHELL_VERSION_CONFIGS.put(998228807, new VrShellConfig("X.07o", "A00", "X.07n", "A00"));
        VRSHELL_VERSION_CONFIGS.put(1026370745, new VrShellConfig("X.05N", "A00", "X.05M", "A00"));
        VRSHELL_VERSION_CONFIGS.put(1028758766, new VrShellConfig("X.08B", "A00", "X.08A", "A00"));

    }

    private static final Map<Integer, VersionConfig> VERSION_CONFIGS = new HashMap<>();
    static {
        VersionConfig v207 = new VersionConfig(
                "X.0aG", "A00", "X.0b7",
                "X.0ZV", "X.0aH", "A0L", "A0E",
                "X.0aL", "A06",
                "X.0rI", "A0o", "A0y", "X.0Zk",
                "A07", "A00",
                null,
                null, null);

        VERSION_CONFIGS.put(675101053, v207);

        VersionConfig v206 = new VersionConfig(
                "X.0Nk", "A00", "X.10U",
                "X.0No", "X.0Nj", "A0L", "A0E",
                "X.0MW", "A05",
                "X.0r8", "A0n", "A12", "X.0MN",
                "A07", "A00",
                "A01",
                "X.0td", "A00");
        VERSION_CONFIGS.put(674401129, v206);
        VERSION_CONFIGS.put(674401131, v206);

        VersionConfig v205 = new VersionConfig(
                "X.0UY", "A00", "X.0Wf",
                "X.0RH", "X.0UZ", "A0M", "A0E",
                "X.0cH", "A04",
                "X.0nw", "A0o", "A11", "X.0Vw",
                "A08", "A00",
                null,
                null, null);
        VERSION_CONFIGS.put(673301462, v205);
        VERSION_CONFIGS.put(673301368, v205);

        VersionConfig v204 = new VersionConfig(
                "X.1qV", "A00", "X.10C",
                "X.0xS", "X.1qW", "A0M", "A0E",
                "X.1qU", "A04",
                "X.12d", "A0l", "A10", "X.1zm",
                "A07", "A00",
                null,
                null, null);
        VERSION_CONFIGS.put(672201326, v204);

        VersionConfig v203pro = new VersionConfig(
                "X.08f", "A00", "X.0ok",
                "X.08i", "X.08g", "A0L", null,
                null, null,
                "X.0fT", "A0v", "A19", "X.07d",
                "A07", "A00",
                null,
                null, null);
        VERSION_CONFIGS.put(671701119, v203pro);

        VERSION_CONFIGS.put(671701082, new VersionConfig(
                "X.08f", "A00", "X.0ok",
                "X.08i", "X.08g", "A0L", null,
                null, null,
                "X.0fT", "A0v", "A19", "X.07d",
                "A07", "A00",
                null,
                null, null));
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }

        Log.i(TAG, "Loaded into " + lpparam.packageName + " (pid=" + android.os.Process.myPid() + ")");

        installPreferenceHooks(lpparam);

        try {
            installGateKeyHooks(lpparam);
        } catch (Throwable t) {
            Log.e(TAG, "Gatekeeper-key hook installation failed", t);
        }

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
                    + "version, so Home/Passthrough/Meta-AI fixes will NOT be applied this run.", t);
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
            Log.e(TAG, "VERSION: failed to read installed versionCode - Home/Passthrough/Meta-AI "
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
                    + "Home/Passthrough-row/Passthrough-page fixes will NOT apply this run - trace this "
                    + "version's APK (see README.md) and add an entry to VERSION_CONFIGS to support it. "
                    + "The double-tap gesture force and /travel_mode still work regardless, since those "
                    + "aren't version-specific.");
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

        installPassthroughPageFix(lpparam, cfg);
        installPassthroughInstantiationTrace(lpparam, cfg);

        if (cfg.pageSyncClass == null) {
            Log.i(TAG, "PASSTHROUGH PAGE SYNC: pageSyncClass not yet traced for this version - skipping");
        } else {
            installPageSyncTrace(lpparam, cfg);
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
                            if (V81_PASSTHROUGH_URI.equals(uri) || V81_TRAVEL_MODE_URI.equals(uri)) {
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

                                Object passthroughItem = ctor.newInstance(
                                        icon, null, smallSize,
                                        "passthrough_nav_item", PASSTHROUGH_TITLE, V81_PASSTHROUGH_URI, 0, false);
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
                                list.add(insertIdx, passthroughItem);
                                param.setResult(list);
                                Log.i(TAG, "V81: injected /passthrough and /travel_mode nav items at index " + insertIdx
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

    /** Hides Meta AI section if toggle is off */
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

    /** Unhides Home section */
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

    /** Backup method to bring Home section back. Only when homeAdderMethod is null */
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

    /** Forces passthrough section to be populated */
    private void installPassthroughPageFix(final LoadPackageParam lpparam, final VersionConfig cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    cfg.pageSectionClass, lpparam.classLoader, cfg.pageGetterMethod,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!cfg.passthroughPageClass.equals(param.thisObject.getClass().getName())) {

                                Log.i(TAG, "PASSTHROUGH PAGE: " + cfg.pageGetterMethod + " fired for OTHER page "
                                        + param.thisObject.getClass().getName() + "@"
                                        + System.identityHashCode(param.thisObject) + " (not Passthrough - ignoring)");
                                return;
                            }
                            try {
                                Object holder = XposedHelpers.getObjectField(param.thisObject, cfg.backingFieldOuter);
                                Object backingList = XposedHelpers.getObjectField(holder, cfg.backingFieldInner);
                                ArrayList<Object> replacement =
                                        new ArrayList<>((java.util.Collection<?>) backingList);
                                Log.i(TAG, "PASSTHROUGH PAGE: " + cfg.pageSectionClass + "." + cfg.pageGetterMethod
                                        + " override fired for " + cfg.passthroughPageClass + "@"
                                        + System.identityHashCode(param.thisObject) + " - raw backing list ("
                                        + cfg.backingFieldOuter + "." + cfg.backingFieldInner + ") has "
                                        + replacement.size() + " item(s)");
                                param.setResult(replacement);
                            } catch (Throwable t) {
                                Log.e(TAG, "PASSTHROUGH PAGE: " + cfg.pageSectionClass + "." + cfg.pageGetterMethod
                                        + " override hook body failed - reading "
                                        + cfg.backingFieldOuter + "/" + cfg.backingFieldInner
                                        + " off " + param.thisObject.getClass().getName(), t);
                            }
                        }
                    });
            Log.i(TAG, "PASSTHROUGH PAGE: hooked " + cfg.pageSectionClass + "." + cfg.pageGetterMethod
                    + " (class-name filtered, no capture step)");
        } catch (Throwable t) {
            Log.e(TAG, "PASSTHROUGH PAGE: failed to hook " + cfg.pageSectionClass + "." + cfg.pageGetterMethod
                    + " - wrong for this versionCode's VERSION_CONFIGS entry?", t);
        }
    }

    /** Logs when the Passthrough page gets created */
    private void installPassthroughInstantiationTrace(final LoadPackageParam lpparam, final VersionConfig cfg) {
        try {
            Class<?> pageClass = XposedHelpers.findClass(cfg.passthroughPageClass, lpparam.classLoader);
            XposedBridge.hookAllConstructors(pageClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    StringBuilder sb = new StringBuilder();
                    StackTraceElement[] trace = new Throwable().getStackTrace();
                    int limit = Math.min(trace.length, 35);
                    for (int i = 0; i < limit; i++) {
                        sb.append("\n    at ").append(trace[i]);
                    }
                    Log.i(TAG, "PASSTHROUGH PAGE: " + cfg.passthroughPageClass + "@"
                            + System.identityHashCode(param.thisObject) + " CONSTRUCTED - call chain:" + sb);
                }
            });
            Log.i(TAG, "PASSTHROUGH PAGE: hooked all constructors of " + cfg.passthroughPageClass
                    + " (instantiation trace only, no override)");
        } catch (Throwable t) {
            Log.e(TAG, "PASSTHROUGH PAGE: failed to hook constructors of " + cfg.passthroughPageClass, t);
        }
    }

    /** Just logs the Passthrough page's data */
    private void installPageSyncTrace(final LoadPackageParam lpparam, final VersionConfig cfg) {
        try {
            Class<?> syncClass = XposedHelpers.findClass(cfg.pageSyncClass, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(syncClass, cfg.pageSyncMethod, cfg.pageSyncClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            logSyncState("BEFORE", param, cfg);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            logSyncState("AFTER", param, cfg);
                        }
                    });
            Log.i(TAG, "PASSTHROUGH PAGE SYNC: hooked " + cfg.pageSyncClass + "." + cfg.pageSyncMethod
                    + " (trace only, no override)");
        } catch (Throwable t) {
            Log.e(TAG, "PASSTHROUGH PAGE SYNC: failed to hook " + cfg.pageSyncClass + "." + cfg.pageSyncMethod, t);
        }
    }

    private void logSyncState(String when, XC_MethodHook.MethodHookParam param, VersionConfig cfg) {
        try {
            Object syncObj = param.args[0];
            if (syncObj == null) {
                Log.i(TAG, "PASSTHROUGH PAGE SYNC: " + when + " fired with a null " + cfg.pageSyncClass + " arg");
                return;
            }
            Object page = XposedHelpers.getObjectField(syncObj, "A01");
            if (page == null || !cfg.passthroughPageClass.equals(page.getClass().getName())) {
                return;
            }
            Object holder = XposedHelpers.getObjectField(page, cfg.backingFieldOuter);
            Object backingList = XposedHelpers.getObjectField(holder, cfg.backingFieldInner);
            int size = backingList == null ? -1 : ((java.util.Collection<?>) backingList).size();
            Log.i(TAG, "PASSTHROUGH PAGE SYNC: " + when + " " + cfg.pageSyncClass + "." + cfg.pageSyncMethod
                    + " fired for " + cfg.passthroughPageClass + "@" + System.identityHashCode(page)
                    + " - raw backing list has " + size + " item(s)");
        } catch (Throwable t) {
            Log.e(TAG, "PASSTHROUGH PAGE SYNC: " + when + " hook body failed", t);
        }
    }

    private static final String VRSHELL_SENSOR_METHOD = "onSensorChanged";
    private static final String PASSTHROUGH_ON_DEMAND_PREF = "passthrough_on_demand_enabled";

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

    /** Reads VrShells version and hooks the sensor method */
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
            Log.e(TAG, "VRSHELL VERSION: no hardcoded support for versionCode=" + versionCode + " yet "
                    + "(or this is v201, which needs its own VRSHELL_VERSION_CONFIGS entry traced - see "
                    + "its javadoc). Trace this version's VrShell.apk and add an entry to "
                    + "VRSHELL_VERSION_CONFIGS to support it.");
            return;
        }

        try {
            final Class<?> outerClass = lpparam.classLoader.loadClass(cfg.outerClass);
            XposedHelpers.findAndHookMethod(
                    cfg.sensorClass, lpparam.classLoader, VRSHELL_SENSOR_METHOD,
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
            Log.i(TAG, "VRSHELL DOUBLE-TAP: hooked " + cfg.sensorClass + "." + VRSHELL_SENSOR_METHOD
                    + " - now skips the whole method while " + PASSTHROUGH_ON_DEMAND_PREF
                    + " reads false, which blocks the forced re-enable directly");
        } catch (Throwable t) {
            Log.e(TAG, "VRSHELL DOUBLE-TAP: failed to install fix for " + cfg.sensorClass + "."
                    + VRSHELL_SENSOR_METHOD + " - wrong for this versionCode's VRSHELL_VERSION_CONFIGS entry?", t);
        }
    }

    /** Scans every (String) -> boolean method in the app and hooks any that are asked about a key in FORCE_ENABLED_KEYS */
    private void installGateKeyHooks(LoadPackageParam lpparam) {
        Set<String> classNames = enumerateClassNames(lpparam);
        int hooked = 0;
        for (String className : classNames) {
            Class<?> clazz;
            try {
                clazz = lpparam.classLoader.loadClass(className);
            } catch (Throwable t) {
                continue;
            }
            Method[] methods;
            try {
                methods = clazz.getDeclaredMethods();
            } catch (Throwable t) {
                continue;
            }
            for (Method m : methods) {
                if (Modifier.isAbstract(m.getModifiers())) continue;
                if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 1 || params[0] != String.class) continue;
                try {
                    XposedBridge.hookMethod(m, new GateHook());
                    hooked++;
                } catch (Throwable t) {

                }
            }
        }
        Log.i(TAG, "GATE: scanned " + classNames.size() + " class(es), hooked " + hooked
                + " candidate(s) matching (String) -> boolean");
    }

    /** Runs after every hooked (String) -> boolean call. If the key is one we care about, forces the result to true */
    private class GateHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.args.length != 1 || !(param.args[0] instanceof String)) return;
            String key = (String) param.args[0];
            if (!FORCE_ENABLED_KEYS.contains(key)) return;
            Object realResult = param.getResult();
            param.setResult(Boolean.TRUE);
            Log.i(TAG, "GATE: forced '" + key + "' -> true (real result was " + realResult + ") via "
                    + param.method);
        }
    }

    /** Finds every class name inside this app, so installGateKeyHooks has something to scan */
    private Set<String> enumerateClassNames(LoadPackageParam lpparam) {
        Set<String> result = new HashSet<>();
        try {
            ClassLoader cl = lpparam.classLoader;

            Class<?> baseDexClassLoaderClass = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pathListField = baseDexClassLoaderClass.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);

            Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] dexElements = (Object[]) dexElementsField.get(pathList);

            for (Object element : dexElements) {

                Field dexFileField;
                try {
                    dexFileField = element.getClass().getDeclaredField("dexFile");
                } catch (NoSuchFieldException e) {
                    continue;
                }
                dexFileField.setAccessible(true);
                Object dexFile = dexFileField.get(element);
                if (dexFile == null) continue;

                Method entriesMethod = dexFile.getClass().getMethod("entries");
                @SuppressWarnings("unchecked")
                Enumeration<String> entries = (Enumeration<String>) entriesMethod.invoke(dexFile);
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement();
                    if (matchesScanPrefix(name)) {
                        result.add(name);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "GATE: class enumeration via classloader reflection failed", t);
        }
        return result;
    }

    /** Keeps the gate-key scan from touching random system classes */
    private boolean matchesScanPrefix(String className) {
        for (String prefix : SCAN_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }
}