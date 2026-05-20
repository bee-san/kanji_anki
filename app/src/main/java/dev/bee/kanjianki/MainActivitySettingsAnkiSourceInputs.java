package dev.bee.kanjianki;

import android.widget.EditText;
import java.util.Locale;

final class MainActivitySettingsAnkiSourceInputs {
    private final MainActivitySettings activity;

    MainActivitySettingsAnkiSourceInputs(MainActivitySettings activity) {
        this.activity = activity;
    }

    EditText fieldInput(String value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(value == null ? "" : value.trim());
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText rankInput(int value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.format(Locale.ROOT, "%d", value));
        input.setTextSize(22);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    EditText decimalInput(double value) {
        EditText input = new EditText(activity);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.ROOT, "%.1f", value));
        input.setTextSize(20);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

}
