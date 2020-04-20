package ly.count.android.demo;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;
import ly.count.android.sdk.CountlyConfig;
import ly.count.android.sdk.CrashFilterCallback;
import ly.count.android.sdk.DeviceId;
import ly.count.android.sdk.RemoteConfig;
import ly.count.android.sdk.messaging.CountlyPush;

import static ly.count.android.sdk.Countly.TAG;

public class App extends Application {
    /** You should use try.count.ly instead of YOUR_SERVER for the line below if you are using Countly trial service */
    final String COUNTLY_SERVER_URL = "YOUR_SERVER";
    final String COUNTLY_APP_KEY = "YOUR_APP_KEY";

    private BroadcastReceiver messageReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Countly.applicationOnCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                // Create the NotificationChannel
                NotificationChannel channel = new NotificationChannel(CountlyPush.CHANNEL_ID, getString(R.string.countly_hannel_name), NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(getString(R.string.countly_channel_description));

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();

                Uri soundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"+ getApplicationContext().getPackageName() + "/" + R.raw.notif_sample);

                channel.setSound(soundUri, audioAttributes);
                notificationManager.createNotificationChannel(channel);
            }
        }

        Context appC = getApplicationContext();

        HashMap<String, String> customHeaderValues = new HashMap<>();
        customHeaderValues.put("foo", "bar");

        Map<String, Object> automaticViewSegmentation = new HashMap<>();

        automaticViewSegmentation.put("One", 2);
        automaticViewSegmentation.put("Three", 4.44d);
        automaticViewSegmentation.put("Five", "Six");

        //Countly.sharedInstance().setConsent(new String[]{Countly.CountlyFeatureNames.push, Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.location, Countly.CountlyFeatureNames.attribution, Countly.CountlyFeatureNames.crashes, Countly.CountlyFeatureNames.events, Countly.CountlyFeatureNames.starRating, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.views}, false);
        //Log.i(demoTag, "Before calling init. This should return 'false', the value is:" + Countly.sharedInstance().isInitialized());
        CountlyConfig config = (new CountlyConfig(appC, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)).setIdMode(DeviceId.Type.OPEN_UDID)
                //.enableTemporaryDeviceIdMode()
                .enableCrashReporting().setLoggingEnabled(true).enableCrashReporting().setViewTracking(true).setAutoTrackingUseShortName(true)
                .setRequiresConsent(true).setConsentEnabled(new String[]{Countly.CountlyFeatureNames.push, Countly.CountlyFeatureNames.sessions, Countly.CountlyFeatureNames.location, Countly.CountlyFeatureNames.attribution, Countly.CountlyFeatureNames.crashes, Countly.CountlyFeatureNames.events, Countly.CountlyFeatureNames.starRating, Countly.CountlyFeatureNames.users, Countly.CountlyFeatureNames.views})
                .addCustomNetworkRequestHeaders(customHeaderValues).setPushIntentAddMetadata(true).setRemoteConfigAutomaticDownload(true, new RemoteConfig.RemoteConfigCallback() {
                    @Override
                    public void callback(String error) {
                        if(error == null) {
                            Log.d(Countly.TAG, "Automatic remote config download has completed");
                        } else {
                            Log.d(Countly.TAG, "Automatic remote config download encountered a problem, " + error);
                        }
                    }
                })
                .setRemoteConfigAutomaticDownload(true, null)
                .setParameterTamperingProtectionSalt("SampleSalt")
                .setAutomaticViewSegmentation(automaticViewSegmentation)
                .setAutoTrackingExceptions(new Class[]{ActivityExampleCustomEvents.class})
                .setTrackOrientationChanges(true)
                .setRecordAllThreadsWithCrash()
                .setCrashFilterCallback(new CrashFilterCallback() {
                    @Override
                    public boolean filterCrash(String crash) {
                        return crash.contains("crash");
                    }
                })
                .setApplication(this)
                .setRecordAppStartTime(true)
                ;
        Countly.sharedInstance().init(config);
        //Log.i(demoTag, "After calling init. This should return 'true', the value is:" + Countly.sharedInstance().isInitialized());

        CountlyPush.init(this, Countly.CountlyMessagingMode.PRODUCTION);
        CountlyPush.setNotificationAccentColor(255, 213, 89, 134);

        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        String token = task.getResult().getToken();
                        CountlyPush.onTokenRefresh(token);
                    }
                });

        /* Register for broadcast action if you need to be notified when Countly message received */
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Intent sentIntent = intent.getParcelableExtra(CountlyPush.EXTRA_INTENT);
                sentIntent.setExtrasClassLoader(CountlyPush.class.getClassLoader());

                Bundle bun = sentIntent.getParcelableExtra(CountlyPush.EXTRA_MESSAGE);
                CountlyPush.Message message;

                int actionIndex = sentIntent.getIntExtra(CountlyPush.EXTRA_ACTION_INDEX, -100);

                String msg = "NULL";

                if (bun != null) {
                    message = bun.getParcelable(CountlyPush.EXTRA_MESSAGE);
                    if (message != null) {
                        msg = message.message();
                    }
                }

                Log.i("Countly", "[CountlyActivity] Got a message, :[" + msg + "]");

            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(CountlyPush.NOTIFICATION_BROADCAST);
        registerReceiver(messageReceiver, filter);
    }
}
