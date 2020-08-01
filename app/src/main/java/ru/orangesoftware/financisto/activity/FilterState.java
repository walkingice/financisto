package ru.orangesoftware.financisto.activity;

import android.content.Context;
import android.widget.ImageButton;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.filter.WhereFilter;

public class FilterState {

    public static void updateFilterColor(Context context, WhereFilter filter, ImageButton button) {
        int color = filter.isEmpty()
                ? context.getResources().getColor(R.color.bottom_bar_button_fg_color_disabled)
                : context.getResources().getColor(R.color.bottom_bar_button_fg_color);
        button.setColorFilter(color);
    }

}
