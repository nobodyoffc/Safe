package com.fc.safe;

import android.app.Application;
import android.os.Bundle;
import android.widget.Toast;
import com.fc.safe.db.DatabaseManager;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.orhanobut.hawk.Hawk;
import com.fc.safe.utils.BackgroundTimeoutManager;
import java.util.ArrayList;
import java.util.List;

public class SafeApplication extends Application {
    public static final int TOAST_LASTING = Toast.LENGTH_SHORT;
    private static final List<String> fidList = new ArrayList<>();
    
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize TimberLogger at the application level
        TimberLogger.init("SafeApp");
        
        // Initialize Hawk at the application level
        Hawk.init(this).build();
        
        // Register activity lifecycle callbacks
        registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(android.app.Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(android.app.Activity activity) {}

            @Override
            public void onActivityResumed(android.app.Activity activity) {
                BackgroundTimeoutManager.onAppForeground(activity);
            }

            @Override
            public void onActivityPaused(android.app.Activity activity) {
                BackgroundTimeoutManager.onAppBackground();
            }

            @Override
            public void onActivityStopped(android.app.Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(android.app.Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        // Close the database when the application is terminating
        DatabaseManager.shutdown();
    }

    /**
     * Get the global fidList
     * @return List of fids
     */
    public static synchronized List<String> getFidList() {
        return new ArrayList<>(fidList);
    }

    /**
     * Add a fid to the global list
     * @param fid The fid to add
     */
    public static synchronized void addFid(String fid) {
        if (!fidList.contains(fid)) {
            fidList.add(fid);
        }
    }

    /**
     * Remove a fid from the global list
     * @param fid The fid to remove
     */
    public static synchronized void removeFid(String fid) {
        fidList.remove(fid);
    }

    /**
     * Clear all fids from the global list
     */
    public static synchronized void clearFidList() {
        fidList.clear();
    }
} 