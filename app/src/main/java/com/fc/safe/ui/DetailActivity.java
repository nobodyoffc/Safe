package com.fc.safe.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.utils.ToolbarUtils;

public class DetailActivity extends AppCompatActivity {
    public final static String TAG = "DetailActivity";
    public static final String EXTRA_ENTITY_JSON = "extra_entity_json";
    public static final String EXTRA_ENTITY_CLASS = "extra_entity_class";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Intent intent = getIntent();
        String entityJson = intent.getStringExtra(EXTRA_ENTITY_JSON);
        String className = intent.getStringExtra(EXTRA_ENTITY_CLASS);
        FcEntity entity = null;
        Class<? extends FcEntity> entityClass = null;
        try {
            if (className != null) {
                entityClass = (Class<? extends FcEntity>) Class.forName(className);
            }
            if (entityJson != null && entityClass != null) {
                entity = FcEntity.fromJson(entityJson, entityClass);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "DetailActivity onCreate: Error loading entity: " + e.getMessage());
        }

        if (entity == null) {
            Toast.makeText(this, getString(R.string.error_no_entity_data_available), SafeApplication.TOAST_LASTING).show();
            finish();
            return;
        }

        ToolbarUtils.setupToolbar(this, entityClass.getSimpleName().replace("Detail","") + " "+getString(R.string.detail));

        TimberLogger.d(TAG, "DetailActivity onCreate: Creating DetailFragment");
        DetailFragment detailFragment = DetailFragment.newInstance(entity, entityClass);
        if (findViewById(R.id.fragment_container) == null) {
            TimberLogger.e(TAG, "DetailActivity onCreate: fragment_container not found in layout");
        } else {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, detailFragment);
            transaction.commit();
        }
    }
} 