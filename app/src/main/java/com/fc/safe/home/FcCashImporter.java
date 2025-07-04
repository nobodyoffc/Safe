package com.fc.safe.home;

import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.safe.R;
import com.fc.safe.ui.SingleInputActivity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FcCashImporter {
    private final Context context;
    private final OnImportListener listener;
    private String type;
    private List<Cash> finalCashList;

    public interface OnImportListener {
        void onImportSuccess(List<Cash> result);
        void onImportError(String error);
    }

    public FcCashImporter(Context context, OnImportListener listener) {
        this.context = context;
        this.listener = listener;
        this.finalCashList = new ArrayList<>();
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Cash> importEntity(String jsonText) {
        if (jsonText.isEmpty()) {
            listener.onImportError("Please input JSON text");
            return null;
        }
        byte[] jsonBytes = jsonText.getBytes(StandardCharsets.UTF_8);

        if(jsonBytes==null || jsonBytes.length==0)return null;

        try(InputStream is = new ByteArrayInputStream(jsonBytes)){
            return importEntity(is);
        } catch (Exception e) {
            Toast.makeText(context, R.string.failed_to_parse_json, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    @Nullable
    public List<Cash> importEntity(InputStream is) throws Exception {
        List<Cash> cashList = JsonUtils.readMultipleJsonObjectsFromInputStream(is, Cash.class);
        if(cashList.isEmpty()){
            listener.onImportError("No valid Cash objects found");
            return null;
        }

        // Ensure each cash has an ID and calculate CD if birthTime is available
        for (Cash cash : cashList) {
            if (cash.getId() == null) {
                cash.makeId();
            }
            
            // If birthTime is not null, calculate CD
            if (cash.getBirthTime() != null) {
                cash.makeCd();
            }
            
            // Set valid to true for all imported cash
            cash.setValid(true);
        }

        finalCashList.addAll(cashList);
        listener.onImportSuccess(finalCashList);
        return finalCashList;
    }

    public void handleInputResult(Intent data) {
        if (data == null) {
            listener.onImportError("Input cancelled");
            return;
        }

        String input = data.getStringExtra(SingleInputActivity.EXTRA_RESULT);
        if (input != null) {
            // Handle any additional input if needed
            listener.onImportError("Unexpected input result");
        }
    }

    public static void hideKeyboard(View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
} 