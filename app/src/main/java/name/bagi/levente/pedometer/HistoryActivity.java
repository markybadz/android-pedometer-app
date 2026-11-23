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

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Daily step totals for the last 30 days: phone count, and the Amazfit
 * watch count from Health Connect when that is turned on.
 */
public class HistoryActivity extends Activity {
    private static final int DAYS = 30;
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault());

    private TableLayout mTable;
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

        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        mTable = new TableLayout(this);
        mTable.setPadding(pad, pad, pad, pad);
        mTable.setStretchAllColumns(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(mTable);
        setContentView(scroll);

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
                            // Leave the watch column showing "–".
                        }
                    });
        }
    }

    private void render() {
        mTable.removeAllViews();
        if (mShowWatch) {
            addRow(true, getString(R.string.history_day), getString(R.string.history_phone),
                    getString(R.string.history_kcal), getString(R.string.history_watch));
        } else {
            addRow(true, getString(R.string.history_day), getString(R.string.history_phone),
                    getString(R.string.history_kcal));
        }

        LocalDate today = LocalDate.now();
        SharedPreferences state = getSharedPreferences("state", 0);
        // Only trust the live totals if they belong to today (the service
        // archives them at midnight, but may not have run since then).
        boolean stateIsToday = today.toString().equals(state.getString("day", ""));
        int goal = mSettings.getCalorieGoal();
        for (int i = 0; i < DAYS; i++) {
            LocalDate day = today.minusDays(i);
            int phone;
            float kcal;
            if (i == 0) {
                phone = stateIsToday ? state.getInt("steps", 0) : 0;
                kcal = stateIsToday ? state.getFloat("calories", 0) : 0;
            } else {
                phone = History.stepsFor(this, day);
                kcal = History.caloriesFor(this, day);
            }
            String label = (i == 0) ? getString(R.string.today) : day.format(DAY_FORMAT);
            String phoneText = phone >= 0 ? String.format(Locale.getDefault(), "%,d", phone) : "–";
            String kcalText = "–";
            if (kcal >= 0) {
                kcalText = String.format(Locale.getDefault(), "%,d", Math.round(kcal));
                if (goal > 0 && kcal >= goal) kcalText += " ✓";
            }
            if (mShowWatch) {
                Long watch = mWatchSteps.get(day);
                String watchText = watch != null ? String.format(Locale.getDefault(), "%,d", watch) : "–";
                addRow(false, label, phoneText, kcalText, watchText);
            } else {
                addRow(false, label, phoneText, kcalText);
            }
        }
    }

    private void addRow(boolean header, String... cells) {
        TableRow row = new TableRow(this);
        for (int i = 0; i < cells.length; i++) {
            row.addView(cell(cells[i], header, i == 0 ? Gravity.START : Gravity.END));
        }
        mTable.addView(row);
    }

    private TextView cell(String text, boolean header, int gravity) {
        TextView view = new TextView(this);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        view.setTextSize(header ? 14 : 16);
        view.setGravity(gravity);
        if (header) view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setText(text);
        return view;
    }
}
