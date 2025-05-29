package com.fc.fc_ajdk.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.graphics.Color;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.FcUtils;
import com.fc.fc_ajdk.utils.NumberUtils;
import com.fc.fc_ajdk.utils.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fc.fc_ajdk.ui.interfaces.IUserDisplay;
import com.google.android.material.snackbar.Snackbar;

/**
 * AndroidShower implements the IUserDisplay interface for Android.
 * This class handles displaying messages, errors, success notifications, and progress indicators
 * in the Android environment.
 */
public class AndroidShower implements IUserDisplay {
    private static final String TAG = "AndroidShower";
    public static final int DEFAULT_PAGE_SIZE = 40;
    
    private final Context context;
    private final Activity activity;
    private ViewGroup container;
    private AlertDialog progressDialog;
    
    public AndroidShower(Context context, Activity activity, ViewGroup container) {
        this.context = context;
        this.activity = activity;
        this.container = container;
    }
    
    /**
     * Set the container view for Snackbar messages
     */
    public void setContainer(ViewGroup container) {
        this.container = container;
    }
    
    @Override
    public void showMessage(String message) {
        activity.runOnUiThread(() -> {
            if (container != null) {
                Snackbar.make(container, message, Snackbar.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void showError(String error) {
        activity.runOnUiThread(() -> {
            if (container != null) {
                Snackbar.make(container, error, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(0xFFD32F2F) // Red color for errors
                        .show();
            } else {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    @Override
    public void showSuccess(String message) {
        activity.runOnUiThread(() -> {
            if (container != null) {
                Snackbar.make(container, message, Snackbar.LENGTH_SHORT)
                        .setBackgroundTint(0xFF4CAF50) // Green color for success
                        .show();
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void showProgress(String message, boolean indeterminate, int progress) {
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setMessage(message);
            builder.setCancelable(false);
            
            // Create a progress dialog
            progressDialog = builder.create();
            
            // Set up the dialog with progress information
            if (!indeterminate) {
                // For determinate progress, create a custom layout
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(40, 40, 40, 40);
                
                // Add message text view
                TextView messageView = new TextView(context);
                messageView.setText(message);
                layout.addView(messageView);
                
                // Add progress bar
                ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setProgress(progress);
                layout.addView(progressBar);
                
                progressDialog.setView(layout);
            }
            
            progressDialog.show();
        });
    }
    
    @Override
    public void hideProgress() {
        activity.runOnUiThread(() -> {
            // Dismiss the progress dialog if it exists
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }
    
    @Override
    public void showChoiceList(String title, String[] choices, ChoiceCallback callback) {
        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setItems(choices, (dialog, which) -> {
                callback.onChoiceSelected(which);
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                callback.onChoiceCancelled();
            });
            builder.show();
        });
    }

    /**
     * Show or choose items from a list with pagination
     */
    public <T> List<T> showOrChooseListInPages(String title, List<T> list, Integer pageSize, String myFid, boolean choose, Class<T> tClass) {
        if (list == null || list.isEmpty()) {
            showMessage("Empty list to show");
            return null;
        }

        showMessage("Total: " + list.size() + " items.");
        List<T> allChosenItems = new ArrayList<>();
        int totalPages = (int) Math.ceil((double) list.size() / pageSize);

        for (int page = 0; page < totalPages; page++) {
            int startIndex = page * pageSize;
            int endIndex = Math.min((page + 1) * pageSize, list.size());
            List<T> pageItems = list.subList(startIndex, endIndex);

            String pageTitle = String.format("%s (Page %d/%d)", title, page + 1, totalPages);
            List<T> chosenItems = showOrChooseList(pageTitle, pageItems, myFid, choose, tClass);
            
            if (choose && chosenItems != null) {
                allChosenItems.addAll(chosenItems);
            }

            // Only ask to continue if there are more pages
            if (page < totalPages - 1) {
                boolean continueNext = showConfirmDialog("There are " + (totalPages - page - 1) + " pages left. Continue?");
                if (!continueNext) break;
            }
        }

        return allChosenItems.isEmpty() ? null : allChosenItems;
    }

    /**
     * Show or choose items from a list
     */
    public <T> List<T> showOrChooseList(String title, List<T> objectList, String myFid, boolean choose, Class<T> tClass) {
        FcEntity.ShowingRules result = FcEntity.getRules(tClass);
        LinkedHashMap<String, Integer> fieldWidthMap = result.fieldWidthMap();
        List<String> timestampFieldList = result.timestampFieldList();
        List<String> satoshiField = result.satoshiField();
        Map<String, String> heightToTimeFieldMap = result.heightToTimeFieldMap();
        Map<String, String> showFieldNameAs = result.showFieldNameAsMap();
        List<String> replaceWithMeFieldList = result.replaceWithMeFieldList();

        return showOrChooseList(title, objectList, myFid, choose, fieldWidthMap, timestampFieldList, 
                              satoshiField, heightToTimeFieldMap, showFieldNameAs, replaceWithMeFieldList);
    }

    /**
     * Show or choose items from a list with detailed configuration
     */
    public <T> List<T> showOrChooseList(String title, List<T> objectList, String myFid, boolean choose,
                                       LinkedHashMap<String, Integer> fieldWidthMap, List<String> timestampFieldList,
                                       List<String> satoshiField, Map<String, String> heightToTimeFieldMap,
                                       Map<String, String> showFieldNameAs, List<String> replaceWithMeFieldList) {
        if (objectList == null || objectList.isEmpty()) {
            showMessage("Nothing to show.");
            return null;
        }

        String[] fields = fieldWidthMap != null ? fieldWidthMap.keySet().toArray(new String[0]) : new String[0];
        int[] widths = fieldWidthMap != null ? fieldWidthMap.values().stream().mapToInt(Integer::intValue).toArray() : new int[0];

        if (fields.length == 0) {
            showMessage("Empty fields.");
            return null;
        }

        if (widths.length == 0) {
            widths = new int[fields.length];
            Arrays.fill(widths, 20);
        }

        activity.runOnUiThread(() -> {
            // Clear previous views
            container.removeAllViews();

            // Add title
            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextSize(18);
            titleView.setPadding(0, 20, 0, 20);
            container.addView(titleView);

            // Create table layout
            TableLayout tableLayout = new TableLayout(context);
            tableLayout.setStretchAllColumns(true);

            // Add header row
            TableRow headerRow = new TableRow(context);
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                if (showFieldNameAs != null && showFieldNameAs.get(field) != null) {
                    field = showFieldNameAs.get(field);
                }
                TextView headerCell = createTableCell(StringUtils.capitalize(field), true);
                headerRow.addView(headerCell);
            }
            tableLayout.addView(headerRow);

            // Add divider
            View divider = new View(context);
            divider.setBackgroundColor(0xFF808080);
            divider.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            tableLayout.addView(divider);

            // Add data rows
            for (T item : objectList) {
                TableRow row = new TableRow(context);
                for (int i = 0; i < fields.length; i++) {
                    String field = fields[i];
                    Object value = getFieldValue(item, field);

                    // Apply special formatting
                    if (timestampFieldList != null && timestampFieldList.contains(field)) {
                        value = formatTimestamp(value);
                    }
                    if (satoshiField != null && satoshiField.contains(field)) {
                        value = formatSatoshi(value);
                    }
                    if (heightToTimeFieldMap != null && heightToTimeFieldMap.containsKey(field)) {
                        value = FcUtils.heightToShortDate((long) value);
                    }
                    if (replaceWithMeFieldList != null && replaceWithMeFieldList.contains(field) && 
                        myFid != null && myFid.equals(value)) {
                        value = "ME";
                    }

                    TextView cell = createTableCell(String.valueOf(value), false);
                    row.addView(cell);
                }
                tableLayout.addView(row);
            }

            // Wrap table in ScrollView
            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(tableLayout);
            container.addView(scrollView);
        });

        // If choose is true, show dialog for selection
        if (choose) {
            return showSelectionDialog(objectList);
        }

        return null;
    }

    /**
     * Create a table cell TextView with proper styling
     */
    private TextView createTableCell(String text, boolean isHeader) {
        TextView cell = new TextView(context);
        cell.setText(text);
        cell.setPadding(10, 10, 10, 10);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        
        if (isHeader) {
            cell.setTypeface(null, Typeface.BOLD);
        }
        
        return cell;
    }

    /**
     * Show a selection dialog for choosing items from a list
     */
    private <T> List<T> showSelectionDialog(List<T> items) {
        final List<T> selectedItems = new ArrayList<>();
        final boolean[] dialogDismissed = new boolean[1];

        activity.runOnUiThread(() -> {
            // Convert items to array of strings for display
            String[] itemStrings = new String[items.size()];
            boolean[] checkedItems = new boolean[items.size()];
            
            for (int i = 0; i < items.size(); i++) {
                itemStrings[i] = items.get(i).toString();
                checkedItems[i] = false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Select Items");
            builder.setMultiChoiceItems(itemStrings, checkedItems, (dialog, which, isChecked) -> {
                if (isChecked) {
                    selectedItems.add(items.get(which));
                } else {
                    selectedItems.remove(items.get(which));
                }
            });

            builder.setPositiveButton("OK", (dialog, which) -> dialogDismissed[0] = true);
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                selectedItems.clear();
                dialogDismissed[0] = true;
            });

            builder.show();
        });

        // Wait for dialog to be dismissed
        while (!dialogDismissed[0]) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return selectedItems;
    }

    /**
     * Format a timestamp value
     */
    private String formatTimestamp(Object value) {
        if (value == null) return "null";
        if (!(value instanceof Long)) return value.toString();
        
        long timestamp = (Long) value;
        if (timestamp > Constants.TIMESTAMP_2000 && timestamp < Constants.TIMESTAMP_2100) {
            return DateUtils.longToTime(timestamp, DateUtils.SHORT_FORMAT);
        }
        return String.valueOf(timestamp);
    }

    /**
     * Format a satoshi value
     */
    private Object formatSatoshi(Object value) {
        try {
            return NumberUtils.formatNumberValue((Number) value, 10);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Get field value using reflection
     */
    private Object getFieldValue(Object object, String fieldName) {
        try {
            Field field = findField(object.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Find a field in a class hierarchy
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * Show a confirmation dialog
     */
    private boolean showConfirmDialog(String message) {
        final boolean[] result = new boolean[1];
        final boolean[] dialogDismissed = new boolean[1];

        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Confirm");
            builder.setMessage(message);
            builder.setPositiveButton("Yes", (dialog, which) -> {
                result[0] = true;
                dialogDismissed[0] = true;
            });
            builder.setNegativeButton("No", (dialog, which) -> {
                result[0] = false;
                dialogDismissed[0] = true;
            });
            builder.show();
        });

        // Wait for dialog to be dismissed
        while (!dialogDismissed[0]) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return result[0];
    }

    /**
     * Display formatted content in a table-like view.
     * 
     * @param title The title of the content
     * @param headers Array of column headers
     * @param data List of data rows, where each row is a list of values
     * @param columnWidths Array of column widths (optional, will use default if null)
     */
    public void showFormattedContent(String title, String[] headers, List<List<Object>> data, int[] columnWidths) {
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);

            // Create a ScrollView to contain the table
            ScrollView scrollView = new ScrollView(activity);
            TableLayout tableLayout = new TableLayout(activity);
            tableLayout.setStretchAllColumns(true);
            tableLayout.setShrinkAllColumns(true);

            // Add headers
            TableRow headerRow = new TableRow(activity);
            headerRow.setBackgroundColor(Color.LTGRAY);
            for (int i = 0; i < headers.length; i++) {
                TextView header = new TextView(activity);
                header.setText(headers[i]);
                header.setPadding(10, 10, 10, 10);

                if (columnWidths != null && i < columnWidths.length) {
                    header.setWidth(columnWidths[i]);
                }
                headerRow.addView(header);
            }
            tableLayout.addView(headerRow);

            // Add data rows
            for (List<Object> row : data) {
                TableRow tableRow = new TableRow(activity);
                for (int i = 0; i < row.size(); i++) {
                    TextView cell = new TextView(activity);
                    cell.setText(String.valueOf(row.get(i)));
                    cell.setPadding(10, 10, 10, 10);
                    if (columnWidths != null && i < columnWidths.length) {
                        cell.setWidth(columnWidths[i]);
                    }
                    tableRow.addView(cell);
                }
                tableLayout.addView(tableRow);
            }

            scrollView.addView(tableLayout);
            builder.setView(scrollView);

            // Add close button
            builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }
} 