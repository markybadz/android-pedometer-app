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

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

/**
 * Stores the phone's step and calorie totals for each finished day.
 * Keys are ISO dates (2026-11-23) for steps, and the date plus ".kcal"
 * for calories.
 */
public class History {
    private static final String PREFS = "history";
    /** How many days of history to keep. */
    public static final int KEEP_DAYS = 90;

    private static final String KCAL = ".kcal";

    public static void saveDay(Context context, String isoDate, int steps, float kcal) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        SharedPreferences.Editor editor = prefs.edit();
        // Add rather than overwrite, in case the same day was archived twice.
        editor.putInt(isoDate, prefs.getInt(isoDate, 0) + steps);
        editor.putFloat(isoDate + KCAL, prefs.getFloat(isoDate + KCAL, 0f) + kcal);

        String oldest = LocalDate.now().minusDays(KEEP_DAYS).toString();
        for (String key : prefs.getAll().keySet()) {
            if (key.compareTo(oldest) < 0) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    /** Steps recorded for that day, or -1 if nothing was recorded. */
    public static int stepsFor(Context context, LocalDate date) {
        return context.getSharedPreferences(PREFS, 0).getInt(date.toString(), -1);
    }

    /** Calories recorded for that day, or -1 if nothing was recorded. */
    public static float caloriesFor(Context context, LocalDate date) {
        return context.getSharedPreferences(PREFS, 0).getFloat(date.toString() + KCAL, -1f);
    }
}
