package com.fc.safe.home;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.safe.R;
import com.fc.safe.db.CashManager;
import com.fc.safe.utils.CashCardManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Example activity demonstrating the use of CashManager and CashCardManager
 */
public class CashListActivity extends AppCompatActivity {
    private CashManager cashManager;
    private CashCardManager cashCardManager;
    private LinearLayout cashListContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_list);

        // Initialize CashManager
        cashManager = CashManager.getInstance(this);

        // Initialize CashCardManager
        cashListContainer = findViewById(R.id.cash_list_container);
        cashCardManager = new CashCardManager(this, cashListContainer, false, 
            Arrays.asList("Delete", "Copy ID", "Copy Amount"));

        // Set up listeners
        cashCardManager.setOnCashListChangedListener(cashList -> {
            Toast.makeText(this, getString(R.string.selected_cashes_count, cashList.size()), Toast.LENGTH_SHORT).show();
        });

        cashCardManager.setOnMenuItemClickListener((menuItem, cash) -> {
            switch (menuItem) {
                case "Copy ID":
                    Toast.makeText(this, getString(R.string.copied_cash_id, cash.getId()), Toast.LENGTH_SHORT).show();
                    break;
                case "Copy Amount":
                    String amount = FchUtils.satoshiToCoin(cash.getValue()) + " F";
                    Toast.makeText(this, getString(R.string.copied_amount, amount), Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        // Load and display sample cash data
        loadSampleCashData();
    }

    private void loadSampleCashData() {
        // Create sample cash data
        List<Cash> sampleCashes = createSampleCashes();
        
        // Add to database
        cashManager.addAllCash(sampleCashes);
        cashManager.commit();

        // Display in cards
        for (Cash cash : sampleCashes) {
            cashCardManager.addCashCard(cash);
        }
    }

    private List<Cash> createSampleCashes() {
        List<Cash> cashes = new ArrayList<>();

        // Sample cash 1
        Cash cash1 = new Cash();
        cash1.setOwner("FTqiqAyXHnK7uDTXzMap3acvqADK4ZGzts");
        cash1.setValue(FchUtils.coinToSatoshi(1.5));
        cash1.setCd(1000L);
        cash1.setValid(true);
        cash1.setId("0000000000000000000000000000000000000000000000000000000000000000");
        cashes.add(cash1);

        // Sample cash 2
        Cash cash2 = new Cash();
        cash2.setOwner("FTqiqAyXHnK7uDTXzMap3acvqADK4ZGzts");
        cash2.setValue(FchUtils.coinToSatoshi(0.75));
        cash2.setCd(500L);
        cash2.setValid(true);
        cash2.setId("1000000000000000000000000000000000000000000000000000000000000000");
        cashes.add(cash2);

        // Sample cash 3
        Cash cash3 = new Cash();
        cash3.setOwner("FTqiqAyXHnK7uDTXzMap3acvqADK4ZGzts");
        cash3.setValue(FchUtils.coinToSatoshi(2.25));
        cash3.setCd(1500L);
        cash3.setValid(true);
        cash3.setId("2000000000000000000000000000000000000000000000000000000000000000");
        cashes.add(cash3);

        return cashes;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up if needed
    }
} 