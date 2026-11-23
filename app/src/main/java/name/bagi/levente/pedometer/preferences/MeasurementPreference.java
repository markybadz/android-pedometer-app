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
package name.bagi.levente.pedometer.preferences;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceManager;

import name.bagi.levente.pedometer.R;

/**
 * A measurement whose unit depends on the metric/imperial setting
 * (step length in cm or in, body weight in kg or lb). Shows e.g. "70 cm".
 */
public abstract class MeasurementPreference extends EditTextPreference {

    public MeasurementPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            editText.setSelection(editText.getText().length());
        });
        setSummaryProvider(preference -> getText() == null ? ""
                : getText() + " " + getContext().getString(unitResource()));
        setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                if (Float.parseFloat(newValue.toString().trim()) > 0) return true;
            } catch (NumberFormatException ignored) {
            }
            Toast.makeText(getContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    protected boolean isMetric() {
        return PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString("units", "metric").equals("metric");
    }

    /** The unit to show, depending on the metric/imperial setting. */
    protected abstract int unitResource();

    /** Title of the dialog, e.g. "Enter your step length". */
    protected abstract int dialogTitleResource();

    @Override
    protected void onClick() {
        setDialogTitle(getContext().getString(dialogTitleResource())
                + " (" + getContext().getString(unitResource()) + ")");
        super.onClick();
    }
}
