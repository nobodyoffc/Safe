package com.fc.fc_ajdk.ui.interfaces;

/**
 * Interface for handling user display operations.
 * This is a platform-agnostic interface that will be implemented by Android-specific components.
 */
public interface IUserDisplay {
    
    /**
     * Display a message to the user.
     * @param message The message to display
     */
    void showMessage(String message);
    
    /**
     * Display an error message to the user.
     * @param error The error message to display
     */
    void showError(String error);
    
    /**
     * Display a success message to the user.
     * @param success The success message to display
     */
    void showSuccess(String success);
    
    /**
     * Display a progress indicator to the user.
     * @param message The message to display with the progress indicator
     * @param indeterminate Whether the progress is indeterminate
     * @param progress The progress value (0-100) if not indeterminate
     */
    void showProgress(String message, boolean indeterminate, int progress);
    
    /**
     * Hide the progress indicator.
     */
    void hideProgress();
    
    /**
     * Display a choice list to the user.
     * @param title The title of the choice list
     * @param choices The list of choices to display
     * @param callback The callback to be called when the user makes a choice
     */
    void showChoiceList(String title, String[] choices, ChoiceCallback callback);
    
    /**
     * Callback interface for choice operations.
     */
    interface ChoiceCallback {
        /**
         * Called when the user makes a choice.
         * @param choice The index of the chosen item
         */
        void onChoiceSelected(int choice);
        
        /**
         * Called when the user cancels the choice operation.
         */
        void onChoiceCancelled();
    }
} 