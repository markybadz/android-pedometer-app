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
import android.util.AttributeSet;

import name.bagi.levente.pedometer.R;

public class StepLengthPreference extends MeasurementPreference {
    public StepLengthPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected int unitResource() {
        return isMetric() ? R.string.suffix_cm : R.string.suffix_in;
    }

    @Override
    protected int dialogTitleResource() {
        return R.string.step_length_setting_title;
    }
}
