package com.fc.fc_ajdk.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.fc.fc_ajdk.ui.interfaces.IInputer;
import com.fc.fc_ajdk.ui.interfaces.IInputer.InputCallback;
import com.fc.fc_ajdk.utils.ObjectUtils;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AndroidInputer class for handling user input in an Android environment.
 * This class implements the IInputer interface to provide a consistent API
 * for both console and Android environments.
 */
public class AndroidInputer implements IInputer {
    private final Context context;
    private final Activity activity;

    public AndroidInputer(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    public char[] inputPassword(String prompt) {
        return showInputDialog(prompt, true).toCharArray();
    }

    @Override
    public void requestPassword(String message, InputCallback callback) {
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(message);
            
            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            builder.setView(input);
            
            builder.setPositiveButton("OK", (dialog, which) -> {
                String password = input.getText().toString();
                if (password != null && !password.isEmpty()) {
                    callback.onSuccess(password);
                } else {
                    callback.onCancel();
                }
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> callback.onCancel());
            
            builder.show();
        });
    }

    public String inputString(String prompt) {
        return showInputDialog(prompt, false);
    }

    public String inputString(String prompt, String defaultValue) {
        return showInputDialog(prompt, defaultValue, false);
    }

    @Override
    public Long inputLong(String fieldName, Long defaultValue) {
        return 0L;
    }

    @Override
    public Long inputLongWithNull(String ask) {
        return 0L;
    }

    @Override
    public Double inputDouble(String fieldName, Double defaultValue) {
        return 0.0;
    }

    public long inputLong(String prompt) {
        String input = showInputDialog(prompt, false);
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            Toast.makeText(context, "Invalid number format", Toast.LENGTH_SHORT).show();
            return 0;
        }
    }

    @Override
    public char[] input32BytesKey(String ask) {
        return new char[0];
    }

    @Override
    public byte[] inputSymkey32(String ask) {
        return new byte[0];
    }

    @Override
    public String inputMsg() {
        return "";
    }

    @Override
    public byte[] getPasswordBytes() {
        return new byte[0];
    }

    @Override
    public byte[] resetNewPassword() {
        return new byte[0];
    }

    @Override
    public byte[] inputAndCheckNewPassword() {
        return new byte[0];
    }

    @Override
    public String inputStringMultiLine() {
        return "";
    }

    @Override
    public boolean askIfYes(String ask) {
        return false;
    }

    @Override
    public boolean confirmDefault(String name) {
        return false;
    }

    @Override
    public String[] promptAndSet(String fieldName, String[] currentValues) {
        return new String[0];
    }

    @Override
    public String promptAndSet(String fieldName, String currentValue) {
        return "";
    }

    @Override
    public long promptAndSet(String fieldName, long currentValue) {
        return 0;
    }

    @Override
    public Boolean promptAndSet(String fieldName, Boolean currentValue) {
        return null;
    }

    @Override
    public long promptForLong(String fieldName, long currentValue) {
        return 0;
    }

    @Override
    public String[] promptAndUpdate(String fieldName, String[] currentValue) {
        return new String[0];
    }

    @Override
    public String promptAndUpdate(String fieldName, String currentValue) {
        return "";
    }

    @Override
    public long promptAndUpdate(String fieldName, long currentValue) {
        return 0;
    }

    @Override
    public Boolean promptAndUpdate(String fieldName, Boolean currentValue) {
        return null;
    }

    @Override
    public byte[] getPasswordStrFromEnvironment() {
        return new byte[0];
    }

    public long inputLong(String prompt, long defaultValue) {
        String input = showInputDialog(prompt, String.valueOf(defaultValue), false);
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            Toast.makeText(context, "Invalid number format", Toast.LENGTH_SHORT).show();
            return defaultValue;
        }
    }

    public Double inputDouble(String prompt) {
        String input = showInputDialog(prompt, false);
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            Toast.makeText(context, "Invalid number format", Toast.LENGTH_SHORT).show();
            return 0.0;
        }
    }

    @Override
    public Boolean inputBoolean(String fieldName, Boolean defaultValue) {
        return null;
    }

    public double inputDouble(String prompt, double defaultValue) {
        String input = showInputDialog(prompt, String.valueOf(defaultValue), false);
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            Toast.makeText(context, "Invalid number format", Toast.LENGTH_SHORT).show();
            return defaultValue;
        }
    }

    public Boolean inputBoolean(String prompt) {
        return showConfirmDialog(prompt);
    }

    @Override
    public String[] inputStringArray(String ask, int len) {
        return new String[0];
    }

    @Override
    public ArrayList<String> inputStringList(String ask, int len) {
        return null;
    }

    @Override
    public Map<String, String> inputStringStringMap(String askKey, String askValue) {
        return Collections.emptyMap();
    }

    @Override
    public void requestConfigurationParameters(Map<String, String> parameters, InputCallback callback) {

    }

    @Override
    public void requestUserSettings(Map<String, String> settings, InputCallback callback) {

    }

    @Override
    public String inputShare(String share) {
        return "";
    }

    @Override
    public String inputIntegerStr(String ask) {
        return "";
    }

    @Override
    public int inputInt(String ask, int maximum) {
        return 0;
    }

    @Override
    public Integer inputIntegerWithNull(String ask, int maximum) {
        return 0;
    }

    public boolean inputBoolean(String prompt, boolean defaultValue) {
        return showConfirmDialog(prompt, defaultValue);
    }

    public File inputFile(String prompt) {
        String path = showInputDialog(prompt, false);
        return new File(path);
    }

    public File inputFile(String prompt, File defaultValue) {
        String path = showInputDialog(prompt, defaultValue.getPath(), false);
        return new File(path);
    }

    public <T> T inputObject(String prompt, Class<T> type) {
        String input = showInputDialog(prompt, false);
        return ObjectUtils.objectToClass(input, type);
    }

    public <T> T inputObject(String prompt, Class<T> type, T defaultValue) {
        String input = showInputDialog(prompt, defaultValue.toString(), false);
        return ObjectUtils.objectToClass(input, type);
    }

    public <T> List<T> inputList(String prompt, Class<T> elementType) {
        String input = showInputDialog(prompt, false);
        return ObjectUtils.objectToList(input, elementType);
    }

    public <T> List<T> inputList(String prompt, Class<T> elementType, List<T> defaultValue) {
        String input = showInputDialog(prompt, defaultValue.toString(), false);
        return ObjectUtils.objectToList(input, elementType);
    }

    public <K, V> Map<K, V> inputMap(String prompt, Class<K> keyType, Class<V> valueType) {
        String input = showInputDialog(prompt, false);
        return ObjectUtils.objectToMap(input, keyType, valueType);
    }

    public <K, V> Map<K, V> inputMap(String prompt, Class<K> keyType, Class<V> valueType, Map<K, V> defaultValue) {
        String input = showInputDialog(prompt, defaultValue.toString(), false);
        return ObjectUtils.objectToMap(input, keyType, valueType);
    }

    public boolean confirmDefault(String prompt, String defaultValue) {
        return showConfirmDialog(prompt + " (Default: " + defaultValue + ")");
    }

    public boolean confirm(String prompt) {
        return showConfirmDialog(prompt);
    }

    public void showMessage(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public void showError(String error) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
    }

    private String showInputDialog(String prompt, boolean isPassword) {
        return showInputDialog(prompt, "", isPassword);
    }

    private String showInputDialog(String prompt, String defaultValue, boolean isPassword) {
        final String[] result = new String[1];
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(prompt);

            final EditText input = new EditText(context);
            input.setInputType(isPassword ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
            input.setText(defaultValue);
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> result[0] = input.getText().toString());
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            builder.show();
        });

        // Wait for the dialog to be dismissed
        while (result[0] == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return result[0];
    }

    private boolean showConfirmDialog(String prompt) {
        return showConfirmDialog(prompt, false);
    }

    private boolean showConfirmDialog(String prompt, boolean defaultValue) {
        final Boolean[] result = new Boolean[1];
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(prompt);
            builder.setMessage("Do you want to proceed?");
            builder.setPositiveButton("Yes", (dialog, which) -> result[0] = true);
            builder.setNegativeButton("No", (dialog, which) -> result[0] = false);
            builder.show();
        });

        // Wait for the dialog to be dismissed
        while (result[0] == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return result[0];
    }

    @Override
    public Long inputDate(String pattern, String ask) {
        String input = showInputDialog(ask, false);
        try {
            return convertDateToTimestamp(input, pattern);
        } catch (ParseException e) {
            Toast.makeText(context, "Invalid date format", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    @Override
    public long convertDateToTimestamp(String dateStr, String pattern) throws ParseException {
        // Implementation needed
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String inputFid(String ask) {
        return showInputDialog(ask, false);
    }

    @Override
    public String inputPubkey(String ask) {
        return showInputDialog(ask, false);
    }

    @Override
    public String[] inputFidArray(String ask, int len) {
        String input = showInputDialog(ask, false);
        return input.split(",");
    }

    @Override
    public String inputPath(String ask) {
        return showInputDialog(ask, false);
    }

    @Override
    public <T> T chooseOne(T[] values, String showStringFieldName, String ask) {
        // Implementation needed
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public <T> T chooseOneFromList(List<T> values, String showStringFieldName, String ask) {
        // Implementation needed
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public <T> String chooseOneKeyFromMap(Map<String, T> stringTMap, boolean showValue, String showStringFieldName, String ask) {
        // Implementation needed
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public <T> Object chooseOneValueFromMap(Map<String, T> stringTMap, boolean showValue, String showStringFieldName, String ask) {
        // Implementation needed
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public <K, V> Object chooseOneFromMapArray(Map<K, V> map, boolean showValue, boolean returnValue, String ask) {
        // Implementation needed
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isGoodShare(Map<String, String> map) {
        // Implementation needed
        throw new UnsupportedOperationException("Not implemented yet");
    }
} 