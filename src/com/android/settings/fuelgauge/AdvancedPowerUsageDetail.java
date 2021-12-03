/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.fuelgauge;

import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.applications.appinfo.AppButtonsPreferenceController;
import com.android.settings.applications.appinfo.ButtonActionDialogFragment;
import com.android.settings.core.InstrumentedPreferenceFragment;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.fuelgauge.batterytip.BatteryTipPreferenceController;
import com.android.settings.fuelgauge.batterytip.tips.BatteryTip;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.widget.EntityHeaderController;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.utils.StringUtil;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.RadioButtonPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Power usage detail fragment for each app, this fragment contains
 *
 * 1. Detail battery usage information for app(i.e. usage time, usage amount)
 * 2. Battery related controls for app(i.e uninstall, force stop)
 */
public class AdvancedPowerUsageDetail extends DashboardFragment implements
        ButtonActionDialogFragment.AppButtonsDialogListener,
        BatteryTipPreferenceController.BatteryTipListener, RadioButtonPreference.OnClickListener {

    public static final String TAG = "AdvancedPowerDetail";
    public static final String EXTRA_UID = "extra_uid";
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    public static final String EXTRA_FOREGROUND_TIME = "extra_foreground_time";
    public static final String EXTRA_BACKGROUND_TIME = "extra_background_time";
    public static final String EXTRA_SLOT_TIME = "extra_slot_time";
    public static final String EXTRA_LABEL = "extra_label";
    public static final String EXTRA_ICON_ID = "extra_icon_id";
    public static final String EXTRA_POWER_USAGE_PERCENT = "extra_power_usage_percent";
    public static final String EXTRA_POWER_USAGE_AMOUNT = "extra_power_usage_amount";

    private static final String KEY_PREF_FOREGROUND = "app_usage_foreground";
    private static final String KEY_PREF_BACKGROUND = "app_usage_background";
    private static final String KEY_PREF_HEADER = "header_view";
    private static final String KEY_PREF_UNRESTRICTED = "unrestricted_pref";
    private static final String KEY_PREF_OPTIMIZED = "optimized_pref";
    private static final String KEY_PREF_RESTRICTED = "restricted_pref";
    private static final String KEY_FOOTER_PREFERENCE = "app_usage_footer_preference";

    private static final int REQUEST_UNINSTALL = 0;
    private static final int REQUEST_REMOVE_DEVICE_ADMIN = 1;

    @VisibleForTesting
    LayoutPreference mHeaderPreference;
    @VisibleForTesting
    ApplicationsState mState;
    @VisibleForTesting
    ApplicationsState.AppEntry mAppEntry;
    @VisibleForTesting
    BatteryUtils mBatteryUtils;
    @VisibleForTesting
    BatteryOptimizeUtils mBatteryOptimizeUtils;
    @VisibleForTesting
    Preference mForegroundPreference;
    @VisibleForTesting
    Preference mBackgroundPreference;
    @VisibleForTesting
    FooterPreference mFooterPreference;
    @VisibleForTesting
    RadioButtonPreference mRestrictedPreference;
    @VisibleForTesting
    RadioButtonPreference mOptimizePreference;
    @VisibleForTesting
    RadioButtonPreference mUnrestrictedPreference;
    @VisibleForTesting
    boolean enableTriState = true;

    private AppButtonsPreferenceController mAppButtonsPreferenceController;
    private BackgroundActivityPreferenceController mBackgroundActivityPreferenceController;

    // A wrapper class to carry LaunchBatteryDetailPage required arguments.
    private static final class LaunchBatteryDetailPageArgs {
        private String mUsagePercent;
        private String mPackageName;
        private String mAppLabel;
        private String mSlotInformation;
        private int mUid;
        private int mIconId;
        private int mConsumedPower;
        private long mForegroundTimeMs;
        private long mBackgroundTimeMs;
        private boolean mIsUserEntry;
    }

    /** Launches battery details page for an individual battery consumer. */
    public static void startBatteryDetailPage(
            Activity caller, InstrumentedPreferenceFragment fragment,
            BatteryDiffEntry diffEntry, String usagePercent,
            boolean isValidToShowSummary, String slotInformation) {
        final BatteryHistEntry histEntry = diffEntry.mBatteryHistEntry;
        final LaunchBatteryDetailPageArgs launchArgs = new LaunchBatteryDetailPageArgs();
        // configure the launch argument.
        launchArgs.mUsagePercent = usagePercent;
        launchArgs.mPackageName = diffEntry.getPackageName();
        launchArgs.mAppLabel = diffEntry.getAppLabel();
        launchArgs.mSlotInformation = slotInformation;
        launchArgs.mUid = (int) histEntry.mUid;
        launchArgs.mIconId = diffEntry.getAppIconId();
        launchArgs.mConsumedPower = (int) diffEntry.mConsumePower;
        launchArgs.mForegroundTimeMs =
            isValidToShowSummary ? diffEntry.mForegroundUsageTimeInMs : 0;
        launchArgs.mBackgroundTimeMs =
            isValidToShowSummary ? diffEntry.mBackgroundUsageTimeInMs : 0;
        launchArgs.mIsUserEntry = histEntry.isUserEntry();
        startBatteryDetailPage(caller, fragment, launchArgs);
    }

    /** Launches battery details page for an individual battery consumer. */
    public static void startBatteryDetailPage(Activity caller,
            InstrumentedPreferenceFragment fragment, BatteryEntry entry, String usagePercent,
            boolean isValidToShowSummary) {
        final LaunchBatteryDetailPageArgs launchArgs = new LaunchBatteryDetailPageArgs();
        // configure the launch argument.
        launchArgs.mUsagePercent = usagePercent;
        launchArgs.mPackageName = entry.getDefaultPackageName();
        launchArgs.mAppLabel = entry.getLabel();
        launchArgs.mUid = entry.getUid();
        launchArgs.mIconId = entry.iconId;
        launchArgs.mConsumedPower = (int) entry.getConsumedPower();
        launchArgs.mForegroundTimeMs = isValidToShowSummary ? entry.getTimeInForegroundMs() : 0;
        launchArgs.mBackgroundTimeMs = isValidToShowSummary ? entry.getTimeInBackgroundMs() : 0;
        launchArgs.mIsUserEntry = entry.isUserEntry();
        startBatteryDetailPage(caller, fragment, launchArgs);
    }

    private static void startBatteryDetailPage(Activity caller,
            InstrumentedPreferenceFragment fragment, LaunchBatteryDetailPageArgs launchArgs) {
        final Bundle args = new Bundle();
        if (launchArgs.mPackageName == null) {
            // populate data for system app
            args.putString(EXTRA_LABEL, launchArgs.mAppLabel);
            args.putInt(EXTRA_ICON_ID, launchArgs.mIconId);
            args.putString(EXTRA_PACKAGE_NAME, null);
        } else {
            // populate data for normal app
            args.putString(EXTRA_PACKAGE_NAME, launchArgs.mPackageName);
        }

        args.putInt(EXTRA_UID, launchArgs.mUid);
        args.putLong(EXTRA_BACKGROUND_TIME, launchArgs.mBackgroundTimeMs);
        args.putLong(EXTRA_FOREGROUND_TIME, launchArgs.mForegroundTimeMs);
        args.putString(EXTRA_SLOT_TIME, launchArgs.mSlotInformation);
        args.putString(EXTRA_POWER_USAGE_PERCENT, launchArgs.mUsagePercent);
        args.putInt(EXTRA_POWER_USAGE_AMOUNT, launchArgs.mConsumedPower);
        final int userId = launchArgs.mIsUserEntry ? ActivityManager.getCurrentUser()
            : UserHandle.getUserId(launchArgs.mUid);

        new SubSettingLauncher(caller)
                .setDestination(AdvancedPowerUsageDetail.class.getName())
                .setTitleRes(R.string.battery_details_title)
                .setArguments(args)
                .setSourceMetricsCategory(fragment.getMetricsCategory())
                .setUserHandle(new UserHandle(userId))
                .launch();
    }

    private static @UserIdInt int getUserIdToLaunchAdvancePowerUsageDetail(
            BatteryEntry batteryEntry) {
        if (batteryEntry.isUserEntry()) {
            return ActivityManager.getCurrentUser();
        }
        return UserHandle.getUserId(batteryEntry.getUid());
    }

    public static void startBatteryDetailPage(Activity caller,
            InstrumentedPreferenceFragment fragment, String packageName) {
        final Bundle args = new Bundle(3);
        final PackageManager packageManager = caller.getPackageManager();
        args.putString(EXTRA_PACKAGE_NAME, packageName);
        args.putString(EXTRA_POWER_USAGE_PERCENT, Utils.formatPercentage(0));
        try {
            args.putInt(EXTRA_UID, packageManager.getPackageUid(packageName, 0 /* no flag */));
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Cannot find package: " + packageName, e);
        }

        new SubSettingLauncher(caller)
                .setDestination(AdvancedPowerUsageDetail.class.getName())
                .setTitleRes(R.string.battery_details_title)
                .setArguments(args)
                .setSourceMetricsCategory(fragment.getMetricsCategory())
                .launch();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mState = ApplicationsState.getInstance(getActivity().getApplication());
        mBatteryUtils = BatteryUtils.getInstance(getContext());
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final String packageName = getArguments().getString(EXTRA_PACKAGE_NAME);
        if (enableTriState) {
            onCreateForTriState(packageName);
        } else {
            mForegroundPreference = findPreference(KEY_PREF_FOREGROUND);
            mBackgroundPreference = findPreference(KEY_PREF_BACKGROUND);
        }
        mHeaderPreference = findPreference(KEY_PREF_HEADER);

        if (packageName != null) {
            mAppEntry = mState.getEntry(packageName, UserHandle.myUserId());
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        initHeader();
        if (enableTriState) {
            initPreferenceForTriState(getContext());
            final String packageName = mBatteryOptimizeUtils.getPackageName();
            FeatureFactory.getFactory(getContext()).getMetricsFeatureProvider()
                .action(
                    getContext(),
                    SettingsEnums.OPEN_APP_BATTERY_USAGE,
                    packageName);
        } else {
            initPreference(getContext());
        }
    }

    @VisibleForTesting
    void initHeader() {
        final View appSnippet = mHeaderPreference.findViewById(R.id.entity_header);
        final Activity context = getActivity();
        final Bundle bundle = getArguments();
        EntityHeaderController controller = EntityHeaderController
                .newInstance(context, this, appSnippet)
                .setRecyclerView(getListView(), getSettingsLifecycle())
                .setButtonActions(EntityHeaderController.ActionType.ACTION_NONE,
                        EntityHeaderController.ActionType.ACTION_NONE);

        if (mAppEntry == null) {
            controller.setLabel(bundle.getString(EXTRA_LABEL));

            final int iconId = bundle.getInt(EXTRA_ICON_ID, 0);
            if (iconId == 0) {
                controller.setIcon(context.getPackageManager().getDefaultActivityIcon());
            } else {
                controller.setIcon(context.getDrawable(bundle.getInt(EXTRA_ICON_ID)));
            }
        } else {
            mState.ensureIcon(mAppEntry);
            controller.setLabel(mAppEntry);
            controller.setIcon(mAppEntry);
            controller.setIsInstantApp(AppUtils.isInstant(mAppEntry.info));
        }

        if (enableTriState) {
            final long foregroundTimeMs = bundle.getLong(EXTRA_FOREGROUND_TIME);
            final long backgroundTimeMs = bundle.getLong(EXTRA_BACKGROUND_TIME);
            final String slotTime = bundle.getString(EXTRA_SLOT_TIME, null);
            controller.setSummary(getAppActiveTime(foregroundTimeMs, backgroundTimeMs, slotTime));
        }

        controller.done(context, true /* rebindActions */);
    }

    @VisibleForTesting
    void initPreference(Context context) {
        final Bundle bundle = getArguments();
        final long foregroundTimeMs = bundle.getLong(EXTRA_FOREGROUND_TIME);
        final long backgroundTimeMs = bundle.getLong(EXTRA_BACKGROUND_TIME);
        mForegroundPreference.setSummary(
                TextUtils.expandTemplate(getText(R.string.battery_used_for),
                        StringUtil.formatElapsedTime(
                                context,
                                foregroundTimeMs,
                                /* withSeconds */ false,
                                /* collapseTimeUnit */ false)));
        mBackgroundPreference.setSummary(
                TextUtils.expandTemplate(getText(R.string.battery_active_for),
                        StringUtil.formatElapsedTime(
                                context,
                                backgroundTimeMs,
                                /* withSeconds */ false,
                                /* collapseTimeUnit */ false)));
    }

    @VisibleForTesting
    void initPreferenceForTriState(Context context) {
        final String stateString;
        final String footerString;

        if (!mBatteryOptimizeUtils.isValidPackageName()) {
            //Present optimized only string when the package name is invalid.
            stateString = context.getString(R.string.manager_battery_usage_optimized_only);
            footerString = context.getString(
                    R.string.manager_battery_usage_footer_limited, stateString);
        } else if (mBatteryOptimizeUtils.isSystemOrDefaultApp()) {
            //Present unrestricted only string when the package is system or default active app.
            stateString = context.getString(R.string.manager_battery_usage_unrestricted_only);
            footerString = context.getString(
                    R.string.manager_battery_usage_footer_limited, stateString);
        } else {
            //Present default string to normal app.
            footerString = context.getString(R.string.manager_battery_usage_footer);
        }
        mFooterPreference.setTitle(footerString);
        mFooterPreference.setLearnMoreAction(v ->
                startActivityForResult(HelpUtils.getHelpIntent(context,
                        context.getString(R.string.help_url_app_usage_settings),
                        /*backupContext=*/ ""), /*requestCode=*/ 0));
        mFooterPreference.setLearnMoreContentDescription(
                context.getString(R.string.manager_battery_usage_link_a11y));
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FUELGAUGE_POWER_USAGE_DETAIL;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return enableTriState ? R.xml.power_usage_detail : R.xml.power_usage_detail_legacy;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        final Bundle bundle = getArguments();
        final int uid = bundle.getInt(EXTRA_UID, 0);
        final String packageName = bundle.getString(EXTRA_PACKAGE_NAME);

        mAppButtonsPreferenceController = new AppButtonsPreferenceController(
                (SettingsActivity) getActivity(), this, getSettingsLifecycle(), packageName,
                mState, REQUEST_UNINSTALL, REQUEST_REMOVE_DEVICE_ADMIN);
        controllers.add(mAppButtonsPreferenceController);
        if (enableTriState) {
            controllers.add(new UnrestrictedPreferenceController(context, uid, packageName));
            controllers.add(new OptimizedPreferenceController(context, uid, packageName));
            controllers.add(new RestrictedPreferenceController(context, uid, packageName));
        } else {
            mBackgroundActivityPreferenceController = new BackgroundActivityPreferenceController(
                    context, this, uid, packageName);
            controllers.add(mBackgroundActivityPreferenceController);
            controllers.add(new BatteryOptimizationPreferenceController(
                    (SettingsActivity) getActivity(), this, packageName));
        }

        return controllers;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mAppButtonsPreferenceController != null) {
            mAppButtonsPreferenceController.handleActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void handleDialogClick(int id) {
        if (mAppButtonsPreferenceController != null) {
            mAppButtonsPreferenceController.handleDialogClick(id);
        }
    }

    @Override
    public void onBatteryTipHandled(BatteryTip batteryTip) {
        mBackgroundActivityPreferenceController.updateSummary(
                findPreference(mBackgroundActivityPreferenceController.getPreferenceKey()));
    }

    @Override
    public void onRadioButtonClicked(RadioButtonPreference selected) {
        final String selectedKey = selected.getKey();
        updatePreferenceState(mUnrestrictedPreference, selectedKey);
        updatePreferenceState(mOptimizePreference, selectedKey);
        updatePreferenceState(mRestrictedPreference, selectedKey);

        // Logs metric.
        int metricCategory = 0;
        if (selectedKey.equals(mUnrestrictedPreference.getKey())) {
            metricCategory = SettingsEnums.ACTION_APP_BATTERY_USAGE_UNRESTRICTED;
        } else if (selectedKey.equals(mOptimizePreference.getKey())) {
            metricCategory = SettingsEnums.ACTION_APP_BATTERY_USAGE_OPTIMIZED;
        } else if (selectedKey.equals(mRestrictedPreference.getKey())) {
            metricCategory = SettingsEnums.ACTION_APP_BATTERY_USAGE_RESTRICTED;
        }
        if (metricCategory != 0) {
            FeatureFactory.getFactory(getContext()).getMetricsFeatureProvider()
                .action(
                    getContext(),
                    metricCategory,
                    new Pair(ConvertUtils.METRIC_KEY_PACKAGE,
                            mBatteryOptimizeUtils.getPackageName()),
                    new Pair(ConvertUtils.METRIC_KEY_BATTERY_USAGE,
                            getArguments().getString(EXTRA_POWER_USAGE_PERCENT)));
        }
    }

    private void updatePreferenceState(RadioButtonPreference preference, String selectedKey) {
        preference.setChecked(selectedKey.equals(preference.getKey()));
    }

    private void onCreateForTriState(String packageName) {
        mUnrestrictedPreference = findPreference(KEY_PREF_UNRESTRICTED);
        mOptimizePreference = findPreference(KEY_PREF_OPTIMIZED);
        mRestrictedPreference = findPreference(KEY_PREF_RESTRICTED);
        mFooterPreference = findPreference(KEY_FOOTER_PREFERENCE);
        mUnrestrictedPreference.setOnClickListener(this);
        mOptimizePreference.setOnClickListener(this);
        mRestrictedPreference.setOnClickListener(this);

        mBatteryOptimizeUtils = new BatteryOptimizeUtils(
                getContext(), getArguments().getInt(EXTRA_UID), packageName);
    }

    private CharSequence getAppActiveTime(
            long foregroundTimeMs, long backgroundTimeMs, String slotTime) {
        final long totalTimeMs = foregroundTimeMs + backgroundTimeMs;
        final CharSequence usageTimeSummary;
        final PowerUsageFeatureProvider powerFeatureProvider =
                FeatureFactory.getFactory(getContext()).getPowerUsageFeatureProvider(getContext());

        if (totalTimeMs == 0) {
            usageTimeSummary = getText(powerFeatureProvider.isChartGraphEnabled(getContext())
                    ? R.string.battery_not_usage_24hr : R.string.battery_not_usage);
        } else if (slotTime == null) {
            // Shows summary text with past 24 hr or full charge if slot time is null.
            usageTimeSummary = powerFeatureProvider.isChartGraphEnabled(getContext())
                    ? getAppPast24HrActiveSummary(foregroundTimeMs, backgroundTimeMs, totalTimeMs)
                    : getAppFullChargeActiveSummary(
                            foregroundTimeMs, backgroundTimeMs, totalTimeMs);
        } else {
            // Shows summary text with slot time.
            usageTimeSummary = getAppActiveSummaryWithSlotTime(
                    foregroundTimeMs, backgroundTimeMs, totalTimeMs, slotTime);
        }
        return usageTimeSummary;
    }

    private CharSequence getAppFullChargeActiveSummary(
            long foregroundTimeMs, long backgroundTimeMs, long totalTimeMs) {
        // Shows background summary only if we don't have foreground usage time.
        if (foregroundTimeMs == 0 && backgroundTimeMs != 0) {
            return backgroundTimeMs < DateUtils.MINUTE_IN_MILLIS ?
                    getText(R.string.battery_bg_usage_less_minute) :
                    TextUtils.expandTemplate(getText(R.string.battery_bg_usage),
                            StringUtil.formatElapsedTime(
                                    getContext(),
                                    backgroundTimeMs,
                                    /* withSeconds */ false,
                                    /* collapseTimeUnit */ false));
        // Shows total usage summary only if total usage time is small.
        } else if (totalTimeMs < DateUtils.MINUTE_IN_MILLIS) {
            return getText(R.string.battery_total_usage_less_minute);
        // Shows different total usage summary when background usage time is small.
        } else if (backgroundTimeMs < DateUtils.MINUTE_IN_MILLIS) {
            return TextUtils.expandTemplate(
                    getText(backgroundTimeMs == 0 ?
                            R.string.battery_total_usage :
                            R.string.battery_total_usage_and_bg_less_minute_usage),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            totalTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false));
        // Shows default summary.
        } else {
            return TextUtils.expandTemplate(
                    getText(R.string.battery_total_and_bg_usage),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            totalTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            backgroundTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false));
        }
    }

    private CharSequence getAppPast24HrActiveSummary(
            long foregroundTimeMs, long backgroundTimeMs, long totalTimeMs) {
        // Shows background summary only if we don't have foreground usage time.
        if (foregroundTimeMs == 0 && backgroundTimeMs != 0) {
            return backgroundTimeMs < DateUtils.MINUTE_IN_MILLIS
                    ? getText(R.string.battery_bg_usage_less_minute_24hr)
                    : TextUtils.expandTemplate(getText(R.string.battery_bg_usage_24hr),
                            StringUtil.formatElapsedTime(
                                    getContext(),
                                    backgroundTimeMs,
                                    /* withSeconds */ false,
                                    /* collapseTimeUnit */ false));
        // Shows total usage summary only if total usage time is small.
        } else if (totalTimeMs < DateUtils.MINUTE_IN_MILLIS) {
            return getText(R.string.battery_total_usage_less_minute_24hr);
        // Shows different total usage summary when background usage time is small.
        } else if (backgroundTimeMs < DateUtils.MINUTE_IN_MILLIS) {
            return TextUtils.expandTemplate(
                    getText(backgroundTimeMs == 0
                            ? R.string.battery_total_usage_24hr
                            : R.string.battery_total_usage_and_bg_less_minute_usage_24hr),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            totalTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false));
        // Shows default summary.
        } else {
            return TextUtils.expandTemplate(
                    getText(R.string.battery_total_and_bg_usage_24hr),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            totalTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            backgroundTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false));
        }
    }

    private CharSequence getAppActiveSummaryWithSlotTime(
            long foregroundTimeMs, long backgroundTimeMs, long totalTimeMs, String slotTime) {
        // Shows background summary only if we don't have foreground usage time.
        if (foregroundTimeMs == 0 && backgroundTimeMs != 0) {
            return backgroundTimeMs < DateUtils.MINUTE_IN_MILLIS ?
                    TextUtils.expandTemplate(
                            getText(R.string.battery_bg_usage_less_minute_with_period),
                            slotTime) :
                    TextUtils.expandTemplate(getText(R.string.battery_bg_usage_with_period),
                            StringUtil.formatElapsedTime(
                                    getContext(),
                                    backgroundTimeMs,
                                    /* withSeconds */ false,
                                    /* collapseTimeUnit */ false), slotTime);
        // Shows total usage summary only if total usage time is small.
        } else if (totalTimeMs < DateUtils.MINUTE_IN_MILLIS) {
            return TextUtils.expandTemplate(
                    getText(R.string.battery_total_usage_less_minute_with_period), slotTime);
        // Shows different total usage summary when background usage time is small.
        } else if (backgroundTimeMs < DateUtils.MINUTE_IN_MILLIS) {
            return TextUtils.expandTemplate(
                    getText(backgroundTimeMs == 0 ?
                            R.string.battery_total_usage_with_period :
                            R.string.battery_total_usage_and_bg_less_minute_usage_with_period),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            totalTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false), slotTime);
        // Shows default summary.
        } else {
            return TextUtils.expandTemplate(
                    getText(R.string.battery_total_and_bg_usage_with_period),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            totalTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false),
                    StringUtil.formatElapsedTime(
                            getContext(),
                            backgroundTimeMs,
                            /* withSeconds */ false,
                            /* collapseTimeUnit */ false), slotTime);
        }
    }
}