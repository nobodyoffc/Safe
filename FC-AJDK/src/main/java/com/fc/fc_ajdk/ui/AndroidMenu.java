package com.fc.fc_ajdk.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.fc_ajdk.R;
import com.fc.fc_ajdk.utils.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AndroidMenu class for displaying and handling menu items in an Android environment.
 * This class provides similar functionality to the Menu class but uses Android UI components.
 */
public class AndroidMenu {
    private final Context context;
    private final Activity activity;
    private String title;
    private final List<MenuItem> items;
    private Runnable exitAction;

    public AndroidMenu(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.items = new ArrayList<>();
        this.exitAction = () -> {};
    }

    public AndroidMenu(Context context, Activity activity, String title) {
        this(context, activity);
        this.title = title;
    }

    public AndroidMenu(Context context, Activity activity, String title, Runnable exitAction) {
        this(context, activity, title);
        this.exitAction = exitAction;
    }

    /**
     * Add a menu item with a name and action
     */
    public void add(String name, Runnable action) {
        items.add(new MenuItem(name, action));
    }

    /**
     * Add a menu item with a name and action (alias for add method)
     */
    public void addItem(String name, Runnable action) {
        add(name, action);
    }

    /**
     * Add multiple menu items at once
     */
    public void add(String... names) {
        for (String name : names) {
            items.add(new MenuItem(name, null));
        }
    }

    /**
     * Clear all menu items
     */
    public void clear() {
        items.clear();
    }

    /**
     * Set the close listener
     */
    public void setOnCloseListener(Runnable listener) {
        this.exitAction = listener;
    }

    /**
     * Show the menu and handle user selection
     */
    public void showAndSelect() {
        activity.runOnUiThread(() -> {
            // Create the list of menu items
            String[] menuItems = new String[items.size() + 1]; // +1 for exit option
            for (int i = 0; i < items.size(); i++) {
                menuItems[i] = (i + 1) + ". " + items.get(i).getName();
            }
            menuItems[items.size()] = "0. Exit";

            // Create and show the dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            
            // Set title
            if (title != null) {
                TextView titleView = new TextView(context);
                titleView.setText(title);
                titleView.setTextSize(20);
                titleView.setPadding(20, 20, 20, 20);
                builder.setCustomTitle(titleView);
            }

            // Create list view
            ListView listView = new ListView(context);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, 
                android.R.layout.simple_list_item_1, menuItems);
            listView.setAdapter(adapter);

            builder.setView(listView);

            // Handle item selection
            listView.setOnItemClickListener((parent, view, position, id) -> {
                if (position == items.size()) {
                    // Exit option selected
                    exitAction.run();
                } else {
                    // Menu item selected
                    MenuItem selectedItem = items.get(position);
                    if (selectedItem.getAction() != null) {
                        selectedItem.getAction().run();
                    }
                }
            });

            builder.show();
        });
    }

    /**
     * Show the menu (alias for showAndSelect)
     */
    public void show() {
        showAndSelect();
    }

    /**
     * Show a confirmation dialog for an action
     */
    public static boolean askIfToDo(Context context, String message) {
        final boolean[] result = new boolean[1];
        final boolean[] dialogDismissed = new boolean[1];

        new AlertDialog.Builder(context)
            .setTitle("Confirm")
            .setMessage(message + "\nPress 'Yes' to proceed, 'No' to cancel.")
            .setPositiveButton("Yes", (dialog, which) -> {
                result[0] = true;
                dialogDismissed[0] = true;
            })
            .setNegativeButton("No", (dialog, which) -> {
                result[0] = false;
                dialogDismissed[0] = true;
            })
            .show();

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
     * Show a welcome message
     */
    public static void welcome(Context context, String name) {
        Toast.makeText(context, R.string.welcome_to_the_freeverse_with + name, Toast.LENGTH_LONG).show();
    }

    /**
     * Show a message and wait for user acknowledgment
     */
    public static void anyKeyToContinue(Context context, String message) {
        new AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    /**
     * Check if a share map sums to 100%
     */
    public static boolean isFullShareMap(Context context, Map<String, String> map) {
        long sum = 0;
        for (String valueStr : map.values()) {
            try {
                double valueDb = Double.parseDouble(valueStr);
                valueDb = NumberUtils.roundDouble8(valueDb);
                sum += ((long) (valueDb * 10000));
            } catch (Exception ignore) {
            }
        }

        if (sum != 10000) {
            Toast.makeText(context, context.getString(R.string.builder_shares_didn_t_sum_up_to_100_reset_it),
                Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /**
     * Get the menu title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set the menu title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * MenuItem class to hold menu item information
     */
    private static class MenuItem {
        private final String name;
        private final Runnable action;

        public MenuItem(String name, Runnable action) {
            this.name = name;
            this.action = action;
        }

        public String getName() {
            return name;
        }

        public Runnable getAction() {
            return action;
        }
    }
} 