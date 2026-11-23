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

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps content clear of the status bar, navigation bar and camera cutout.
 * From Android 15 apps draw behind these bars (edge-to-edge), and from
 * Android 16 apps can't opt out, so each screen pads its own views.
 */
public final class SystemBars {
    private SystemBars() {
    }

    /** For a top toolbar: room for the status bar (and side cutouts). */
    public static void padTop(View view) {
        apply(view, true, false);
    }

    /** For scrolling content: room for the navigation bar (and side cutouts). */
    public static void padBottom(View view) {
        apply(view, false, true);
    }

    /** For a screen with no toolbar. */
    public static void padAll(View view) {
        apply(view, true, true);
    }

    private static void apply(View view, final boolean top, final boolean bottom) {
        final int left = view.getPaddingLeft();
        final int topPad = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottomPad = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left + bars.left,
                    topPad + (top ? bars.top : 0),
                    right + bars.right,
                    bottomPad + (bottom ? bars.bottom : 0));
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
