package com.fc.fc_ajdk.ui;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.fc.fc_ajdk.ui.interfaces.IInputer;
import com.fc.fc_ajdk.ui.interfaces.IInputer.InputCallback;
import com.fc.fc_ajdk.ui.interfaces.IUserDisplay;

import java.util.List;
import java.util.Map;

/**
 * UIManager class that coordinates UI operations.
 * This class provides a unified interface for all input and display operations.
 */
public class UIManager {
    private final Context context;
    private final Activity activity;
    private final IInputer inputer;
    private final IUserDisplay display;

    public UIManager(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.inputer = new AndroidInputer(context, activity);
        this.display = new AndroidShower(context, activity, null);
    }
    
    /**
     * Set the container view for Snackbar messages
     * @param container The ViewGroup to use as container for Snackbar messages
     */
    public void setDisplayContainer(ViewGroup container) {
        if (display instanceof AndroidShower) {
            ((AndroidShower) display).setContainer(container);
        }
    }

    // Input operations
    public char[] inputPassword(String prompt) {
        return inputer.inputPassword(prompt);
    }

    public String inputText(String prompt) {
        return inputer.inputString(prompt);
    }

    public int inputNumber(String prompt) {
        return inputer.inputInt(prompt, Integer.MAX_VALUE);
    }

    // Callback-based input operations
    public void requestPassword(String prompt, InputCallback callback) {
        inputer.requestPassword(prompt, callback);
    }

    public void requestConfigurationParameters(Map<String, String> parameters, InputCallback callback) {
        inputer.requestConfigurationParameters(parameters, callback);
    }

    public void requestUserSettings(Map<String, String> settings, InputCallback callback) {
        inputer.requestUserSettings(settings, callback);
    }
    
    // Display operations
    public void showMessage(String message) {
        display.showMessage(message);
    }
    
    public void showError(String error) {
        display.showError(error);
    }
    
    public void showSuccess(String success) {
        display.showSuccess(success);
    }
    
    public void showProgress(String message, boolean indeterminate, int progress) {
        display.showProgress(message, indeterminate, progress);
    }
    
    public void hideProgress() {
        display.hideProgress();
    }
    
    public void showChoiceList(String title, String[] choices, IUserDisplay.ChoiceCallback callback) {
        display.showChoiceList(title, choices, callback);
    }

    /**
     * Display formatted content on a single page.
     * This method creates a table-like view with headers and data rows.
     * 
     * @param title The title of the content
     * @param headers Array of column headers
     * @param data List of data rows, where each row is a list of values
     * @param columnWidths Array of column widths (optional, will use default if null)
     */
    public void showFormattedContent(String title, String[] headers, List<List<Object>> data, int[] columnWidths) {
        if (display instanceof AndroidShower) {
            ((AndroidShower) display).showFormattedContent(title, headers, data, columnWidths);
        }
    }

    /**
     * Display formatted content on a single page with default column widths.
     * 
     * @param title The title of the content
     * @param headers Array of column headers
     * @param data List of data rows, where each row is a list of values
     */
    public void showFormattedContent(String title, String[] headers, List<List<Object>> data) {
        showFormattedContent(title, headers, data, null);
    }

    public IInputer getInputer() {
        return inputer;
    }

    public IUserDisplay getDisplay() {
        return display;
    }

    public Context getContext() {
        return context;
    }

    public Activity getActivity() {
        return activity;
    }
}