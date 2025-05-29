package com.fc.fc_ajdk.ui.interfaces;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interface for input operations.
 * This interface defines methods for both synchronous and asynchronous input operations.
 */
public interface IInputer {
    /**
     * Callback interface for asynchronous input operations.
     */
    interface InputCallback {
        /**
         * Called when the user provides input.
         * @param result The result of the user input
         */
        void onSuccess(String result);
        
        /**
         * Called when the user cancels the input operation.
         */
        void onCancel();
    }
    
    /**
     * Request a password from the user.
     * @param ask The message to display to the user
     * @return The password as a char array
     */
    char[] inputPassword(String ask);
    
    /**
     * Request a password from the user asynchronously.
     * @param message The message to display to the user
     * @param callback The callback to be called when the user provides input
     */
    void requestPassword(String message, InputCallback callback);
    
    /**
     * Request a string from the user.
     * @param ask The message to display to the user
     * @return The string input by the user
     */
    String inputString(String ask);
    
    /**
     * Request a string from the user with a default value.
     * @param fieldName The name of the field
     * @param defaultValue The default value
     * @return The string input by the user or the default value if the user didn't input anything
     */
    String inputString(String fieldName, String defaultValue);
    
    /**
     * Request a long value from the user.
     * @param fieldName The name of the field
     * @param defaultValue The default value
     * @return The long value input by the user or the default value if the user didn't input anything
     */
    Long inputLong(String fieldName, Long defaultValue);
    
    /**
     * Request a long value from the user.
     * @param ask The message to display to the user
     * @return The long value input by the user
     */
    Long inputLongWithNull(String ask);
    
    /**
     * Request a double value from the user.
     * @param fieldName The name of the field
     * @param defaultValue The default value
     * @return The double value input by the user or the default value if the user didn't input anything
     */
    Double inputDouble(String fieldName, Double defaultValue);
    
    /**
     * Request a double value from the user.
     * @param ask The message to display to the user
     * @return The double value input by the user
     */
    Double inputDouble(String ask);
    
    /**
     * Request a boolean value from the user.
     * @param fieldName The name of the field
     * @param defaultValue The default value
     * @return The boolean value input by the user or the default value if the user didn't input anything
     */
    Boolean inputBoolean(String fieldName, Boolean defaultValue);
    
    /**
     * Request a boolean value from the user.
     * @param ask The message to display to the user
     * @return The boolean value input by the user
     */
    Boolean inputBoolean(String ask);
    
    /**
     * Request a string array from the user.
     * @param ask The message to display to the user
     * @param len The expected length of the array (0 for any length)
     * @return The string array input by the user
     */
    String[] inputStringArray(String ask, int len);
    
    /**
     * Request a list of strings from the user.
     * @param ask The message to display to the user
     * @param len The expected length of the list (0 for any length)
     * @return The list of strings input by the user
     */
    ArrayList<String> inputStringList(String ask, int len);
    
    /**
     * Request a map of strings from the user.
     * @param askKey The message to display for the key
     * @param askValue The message to display for the value
     * @return The map of strings input by the user
     */
    Map<String, String> inputStringStringMap(String askKey, String askValue);
    
    /**
     * Request configuration parameters from the user.
     * @param parameters The map of parameters to request
     * @param callback The callback to be called when the user provides input
     */
    void requestConfigurationParameters(Map<String, String> parameters, InputCallback callback);
    
    /**
     * Request user settings from the user.
     * @param settings The map of settings to request
     * @param callback The callback to be called when the user provides input
     */
    void requestUserSettings(Map<String, String> settings, InputCallback callback);
    
    /**
     * Request a share value from the user.
     * @param share The name of the share
     * @return The share value input by the user
     */
    String inputShare(String share);
    
    /**
     * Request an integer string from the user.
     * @param ask The message to display to the user
     * @return The integer string input by the user
     */
    String inputIntegerStr(String ask);
    
    /**
     * Request an integer from the user.
     * @param ask The message to display to the user
     * @param maximum The maximum allowed value
     * @return The integer input by the user
     */
    int inputInt(String ask, int maximum);
    
    /**
     * Request an integer from the user with a maximum value.
     * @param ask The message to display to the user
     * @param maximum The maximum allowed value
     * @return The integer input by the user or null if the user didn't input anything
     */
    Integer inputIntegerWithNull(String ask, int maximum);
    
    /**
     * Request a long value from the user.
     * @param ask The message to display to the user
     * @return The long value input by the user
     */
    long inputLong(String ask);
    
    /**
     * Request a 32-byte key from the user.
     * @param ask The message to display to the user
     * @return The key as a char array
     */
    char[] input32BytesKey(String ask);
    
    /**
     * Request a symmetric key from the user.
     * @param ask The message to display to the user
     * @return The key as a byte array
     */
    byte[] inputSymkey32(String ask);
    
    /**
     * Request a message from the user.
     * @return The message input by the user
     */
    String inputMsg();
    
    /**
     * Get password bytes from the user.
     * @return The password as a byte array
     */
    byte[] getPasswordBytes();
    
    /**
     * Reset a new password from the user.
     * @return The new password as a byte array
     */
    byte[] resetNewPassword();
    
    /**
     * Input and check a new password from the user.
     * @return The new password as a byte array
     */
    byte[] inputAndCheckNewPassword();
    
    /**
     * Request a multi-line string from the user.
     * @return The multi-line string input by the user
     */
    String inputStringMultiLine();
    
    /**
     * Ask the user if they want to proceed.
     * @param ask The message to display to the user
     * @return True if the user wants to proceed, false otherwise
     */
    boolean askIfYes(String ask);
    
    /**
     * Confirm the default value.
     * @param name The name of the default value
     * @return True if the user wants to use the default value, false otherwise
     */
    boolean confirmDefault(String name);
    
    /**
     * Prompt and set a value.
     * @param fieldName The name of the field
     * @param currentValues The current values
     * @return The new values
     */
    String[] promptAndSet(String fieldName, String[] currentValues);
    
    /**
     * Prompt and set a string value.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    String promptAndSet(String fieldName, String currentValue);
    
    /**
     * Prompt and set a long value.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    long promptAndSet(String fieldName, long currentValue);
    
    /**
     * Prompt and set a boolean value.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    Boolean promptAndSet(String fieldName, Boolean currentValue);
    
    /**
     * Prompt for a long value.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    long promptForLong(String fieldName, long currentValue);
    
    /**
     * Prompt and update a string array.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    String[] promptAndUpdate(String fieldName, String[] currentValue);
    
    /**
     * Prompt and update a string value.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    String promptAndUpdate(String fieldName, String currentValue);
    
    /**
     * Prompt and update a long value.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    long promptAndUpdate(String fieldName, long currentValue);
    
    /**
     * Prompt and update a boolean value.
     * @param fieldName The name of the field
     * @param currentValue The current value
     * @return The new value
     */
    Boolean promptAndUpdate(String fieldName, Boolean currentValue);
    
    /**
     * Get password string from environment.
     * @return The password as a byte array
     */
    byte[] getPasswordStrFromEnvironment();
    
    /**
     * Input a FID from the user.
     * @param ask The message to display to the user
     * @return The FID input by the user
     */
    String inputFid(String ask);
    
    /**
     * Input a public key from the user.
     * @param ask The message to display to the user
     * @return The public key input by the user
     */
    String inputPubkey(String ask);
    
    /**
     * Input an array of FIDs from the user.
     * @param ask The message to display to the user
     * @param len The expected length of the array (0 for any length)
     * @return The array of FIDs input by the user
     */
    String[] inputFidArray(String ask, int len);
    
    /**
     * Input a path from the user.
     * @param ask The message to display to the user
     * @return The path input by the user
     */
    String inputPath(String ask);
    
    /**
     * Choose one item from a list.
     * @param values The list of values
     * @param showStringFieldName The name of the field to show
     * @param ask The message to display to the user
     * @return The selected item
     */
    <T> T chooseOne(T[] values, String showStringFieldName, String ask);
    
    /**
     * Choose one item from a list.
     * @param values The list of values
     * @param showStringFieldName The name of the field to show
     * @param ask The message to display to the user
     * @return The selected item
     */
    <T> T chooseOneFromList(List<T> values, String showStringFieldName, String ask);
    
    /**
     * Choose one key from a map.
     * @param stringTMap The map
     * @param showValue Whether to show the value
     * @param showStringFieldName The name of the field to show
     * @param ask The message to display to the user
     * @return The selected key
     */
    <T> String chooseOneKeyFromMap(Map<String, T> stringTMap, boolean showValue, String showStringFieldName, String ask);
    
    /**
     * Choose one value from a map.
     * @param stringTMap The map
     * @param showValue Whether to show the value
     * @param showStringFieldName The name of the field to show
     * @param ask The message to display to the user
     * @return The selected value
     */
    <T> Object chooseOneValueFromMap(Map<String, T> stringTMap, boolean showValue, String showStringFieldName, String ask);
    
    /**
     * Choose one item from a map array.
     * @param map The map
     * @param showValue Whether to show the value
     * @param returnValue Whether to return the value
     * @param ask The message to display to the user
     * @return The selected item
     */
    <K, V> Object chooseOneFromMapArray(Map<K, V> map, boolean showValue, boolean returnValue, String ask);
    
    /**
     * Check if a map has good share values.
     * @param map The map to check
     * @return True if the map has good share values, false otherwise
     */
    boolean isGoodShare(Map<String, String> map);
    
    /**
     * Input a date from the user.
     * @param pattern The date pattern
     * @param ask The message to display to the user
     * @return The date as a long value
     */
    Long inputDate(String pattern, String ask);
    
    /**
     * Convert a date string to a timestamp.
     * @param dateStr The date string
     * @param pattern The date pattern
     * @return The timestamp
     */
    long convertDateToTimestamp(String dateStr, String pattern) throws ParseException;
} 