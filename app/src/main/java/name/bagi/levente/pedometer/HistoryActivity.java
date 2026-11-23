/*
 *  Pedometer - Android App
 *  Copyright (C) 2009 Levente Bagi
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package name.bagi.levente.pedometer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The last 30 days: a summary of the past week, then one card per day with
 * steps against the step goal, calories, and the Amazfit watch's steps.
 */
public class HistoryActivity extends AppCompatActivity {
    private static final int DAYS = 30;
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault());

    private LinearLayout mList;
    private PedometerSettings mSettings;
    private final Map<LocalDate, Long> mWatchSteps = new HashMap<LocalDate, Long>();
    private boolean mShowWatch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.history);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mSettings = new PedometerSettings(prefs);
        mShowWatch = mSettings.isWatchEnabled() && WatchSteps.isAvailable(this);

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        mList.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.background));
        scroll.setClipToPadding(false);
        scroll.addView(mList);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background));
        Toolbar toolbar = (Toolbar) getLayoutInflater().inflate(R.layout.toolbar, root, false);
        root.addView(toolbar);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        SystemBars.padTop(toolbar);
        SystemBars.padBottom(scroll);

        render();

        if (mShowWatch) {
            WatchSteps.readDaily(this, mSettings.getWatchSource(), DAYS,
                    new WatchSteps.Callback<List<WatchSteps.DayTotal>>() {
                        @Override
                        public void onResult(List<WatchSteps.DayTotal> result) {
                            if (isFinishing()) return;
                            for (WatchSteps.DayTotal day : result) {
                                mWatchSteps.put(day.date, day.steps);
                            }
                            render();
                        }
                        @Override
                        public void onError(String message) {
                            // Days keep showing phone data only.
                        }
                    });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void render() {
        mList.removeAllViews();
        LocalDate today = LocalDate.now();
        SharedPreferences state = getSharedPreferences("state", 0);
        // Only trust the live totals if they belong to today (the service
        // archives them at midnight, but may not have run since then).
        boolean stateIsToday = today.toString().equals(state.getString("day", ""));
        int stepGoal = mSettings.getStepGoal();
        int kcalGoal = mSettings.getCalorieGoal();

        int[] steps = new int[DAYS];
        float[] kcal = new float[DAYS];
        for (int i = 0; i < DAYS; i++) {
            LocalDate day = today.minusDays(i);
            if (i == 0) {
                steps[i] = stateIsToday ? state.getInt("steps", 0) : 0;
                kcal[i] = stateIsToday ? state.getFloat("calories", 0) : 0;
            } else {
                steps[i] = History.stepsFor(this, day);
                kcal[i] = History.caloriesFor(this, day);
            }
        }

        // Summary of the last 7 days (today included).
        long weekSteps = 0;
        float weekKcal = 0;
        int daysWithData = 0;
        int goalDays = 0;
        for (int i = 0; i < 7; i++) {
            if (steps[i] < 0) continue;
            daysWithData++;
            weekSteps += steps[i];
            if (kcal[i] > 0) weekKcal += kcal[i];
            if (stepGoal > 0 && steps[i] >= stepGoal) goalDays++;
        }
        long average = daysWithData > 0 ? weekSteps / daysWithData : 0;
        addSummaryCard(average, Math.round(weekKcal), goalDays);

        for (int i = 0; i < DAYS; i++) {
            LocalDate day = today.minusDays(i);
            Long watch = mShowWatch ? mWatchSteps.get(day) : null;
            if (steps[i] < 0 && watch == null) continue; // nothing recorded that day
            String label = (i == 0) ? getString(R.string.today)
                    : (i == 1) ? getString(R.string.yesterday) : day.format(DAY_FORMAT);
            addDayCard(label, steps[i], kcal[i], watch, stepGoal, kcalGoal);
        }
    }

    private void addSummaryCard(long averageSteps, int weekKcal, int goalDays) {
        LinearLayout card = card();
        card.addView(text(getString(R.string.history_last_7_days), 12, R.color.text_secondary, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(stat(fmt(averageSteps), getString(R.string.history_avg_steps)));
        row.addView(stat(fmt(weekKcal), getString(R.string.history_week_kcal)));
        row.addView(stat(goalDays + " / 7", getString(R.string.history_goal_days)));
        card.addView(row);
        mList.addView(card);
    }

    private void addDayCard(String label, int steps, float kcal, Long watch, int stepGoal, int kcalGoal) {
        LinearLayout card = card();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(label, 15, R.color.text_primary, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(name);
        boolean goalMet = stepGoal > 0 && steps >= stepGoal;
        String stepsText = steps >= 0 ? fmt(steps) + " " + getString(R.string.suffix_steps) : "–";
        TextView count = text(stepsText + (goalMet ? "  ✓" : ""), 15,
                goalMet ? R.color.accent_steps : R.color.text_primary, true);
        top.addView(count);
        card.addView(top);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressDrawable(getDrawable(R.drawable.bar_steps));
        bar.setMax(100);
        bar.setProgress(stepGoal > 0 && steps > 0 ? Math.min(100, (int) (100L * steps / stepGoal)) : 0);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        barParams.topMargin = dp(10);
        bar.setLayoutParams(barParams);
        card.addView(bar);

        StringBuilder details = new StringBuilder();
        if (kcal >= 0) {
            details.append(fmt(Math.round(kcal))).append(" kcal");
            if (kcalGoal > 0 && kcal >= kcalGoal) details.append(" ✓");
        }
        if (watch != null) {
            if (details.length() > 0) details.append("  ·  ");
            details.append(getString(R.string.history_watch_steps, fmt(watch)));
        }
        if (details.length() > 0) {
            TextView detail = text(details.toString(), 13, R.color.text_secondary, false);
            detail.setPadding(0, dp(8), 0, 0);
            card.addView(detail);
        }
        mList.addView(card);
    }

    //-------------------------------------------------------------------
    // Small view helpers

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private View stat(String value, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        box.addView(text(value, 22, R.color.text_primary, true));
        box.addView(text(label, 12, R.color.text_secondary, false));
        return box;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(getColor(colorRes));
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private String fmt(long number) {
        return String.format(Locale.getDefault(), "%,d", number);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
