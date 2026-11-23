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
import android.content.res.TypedArray;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.preference.EditTextPreference;

import java.util.Locale;

import name.bagi.levente.pedometer.R;

/**
 * A whole-number setting (step goal, calorie goal, age, heart rate).
 * Shows its current value as the summary, e.g. "8,000 steps", opens a
 * number keypad, and rejects anything that isn't a positive number.
 */
public class NumberPreference extends EditTextPreference {
    private String mSuffix;

    public NumberPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NumberPreference);
        mSuffix = a.getString(R.styleable.NumberPreference_valueSuffix);
        a.recycle();
        init();
    }

    private void init() {
        setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setSelection(editText.getText().length());
        });
        setSummaryProvider(preference -> {
            String text = getText();
            try {
                String number = String.format(Locale.getDefault(), "%,d", Integer.parseInt(text.trim()));
                return mSuffix != null ? number + " " + mSuffix : number;
            } catch (RuntimeException e) {
                return text;
            }
        });
        setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                if (Integer.parseInt(newValue.toString().trim()) > 0) return true;
            } catch (NumberFormatException ignored) {
            }
            Toast.makeText(getContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show();
            return false;
        });
    }
}
