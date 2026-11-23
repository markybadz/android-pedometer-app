/*
 *  Pedometer - Android App
 *  Copyright (C) 2009 Levente Bagi
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package name.bagi.levente.pedometer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.time.LocalDate;



/**
 * This is an example of implementing an application service that runs locally
 * in the same process as the application.  The {@link StepServiceController}
 * and {@link StepServiceBinding} classes show how to interact with the
 * service.
 *
 * <p>Notice the use of the {@link NotificationManager} when interesting things
 * happen in the service.  This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 */
public class StepService extends Service {
	private static final String TAG = "name.bagi.levente.pedometer.StepService";
    private static final String CHANNEL_ID = "step_service";
    private static final int NOTIFICATION_ID = 1;
    private static final int SAVE_EVERY_N_STEPS = 20;
    /** State key: true while the user wants counting on (used to restart after reboot). */
    private static final String KEY_COUNTING = "counting_enabled";
    /** State key: the ISO date the current totals belong to. */
    private static final String KEY_DAY = "day";

    /** Whether counting should be running (it was started and not paused or quit). */
    public static boolean isCountingEnabled(Context context) {
        return context.getSharedPreferences("state", 0).getBoolean(KEY_COUNTING, false);
    }

    /** Called by the activity when the user pauses or quits, so counting stays off after a reboot. */
    public static void setCountingEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("state", 0).edit().putBoolean(KEY_COUNTING, enabled).apply();
    }
    private SharedPreferences mSettings;
    private PedometerSettings mPedometerSettings;
    private SharedPreferences mState;
    private SharedPreferences.Editor mStateEditor;
    private Utils mUtils;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    /** True when steps come from the phone's step detector chip rather than the accelerometer. */
    private boolean mUsingHardwareSensor;
    /** ISO date of the day the current totals belong to. */
    private String mDay;
    private StepDetector mStepDetector;
    // private StepBuzzer mStepBuzzer; // used for debugging
    private StepDisplayer mStepDisplayer;
    private PaceNotifier mPaceNotifier;
    private DistanceNotifier mDistanceNotifier;
    private SpeedNotifier mSpeedNotifier;
    private CaloriesNotifier mCaloriesNotifier;
    private SpeakingTimer mSpeakingTimer;
    
    private PowerManager.WakeLock wakeLock;
    private NotificationManager mNM;

    private int mSteps;
    private int mPace;
    private float mDistance;
    private float mSpeed;
    private float mCalories;
    
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class StepBinder extends Binder {
        StepService getService() {
            return StepService.this;
        }
    }
    
    @Override
    public void onCreate() {
        Log.i(TAG, "[SERVICE] onCreate");
        super.onCreate();
        
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        showNotification();
        
        // Load settings
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mPedometerSettings = new PedometerSettings(mSettings);
        mState = getSharedPreferences("state", 0);
        setCountingEnabled(this, true);
        archiveStoredDayIfOld();

        mUtils = Utils.getInstance();
        mUtils.setService(this);
        mUtils.initTTS();

        acquireWakeLock();
        
        // Start detecting
        mStepDetector = new StepDetector();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        registerDetector();

        // Register our receiver for the ACTION_SCREEN_OFF action. This will make our receiver
        // code be called whenever the phone enters standby mode.
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        // Must be the first listener, so a step taken just after midnight
        // is counted towards the new day.
        mStepDetector.addStepListener(mDayWatcher);

        mStepDisplayer = new StepDisplayer(mPedometerSettings, mUtils);
        mStepDisplayer.setSteps(mSteps = mState.getInt("steps", 0));
        mStepDisplayer.addListener(mStepListener);
        mStepDetector.addStepListener(mStepDisplayer);

        mPaceNotifier     = new PaceNotifier(mPedometerSettings, mUtils);
        mPaceNotifier.setPace(mPace = mState.getInt("pace", 0));
        mPaceNotifier.addListener(mPaceListener);
        mStepDetector.addStepListener(mPaceNotifier);

        mDistanceNotifier = new DistanceNotifier(mDistanceListener, mPedometerSettings, mUtils);
        mDistanceNotifier.setDistance(mDistance = mState.getFloat("distance", 0));
        mStepDetector.addStepListener(mDistanceNotifier);
        
        mSpeedNotifier    = new SpeedNotifier(mSpeedListener,    mPedometerSettings, mUtils);
        mSpeedNotifier.setSpeed(mSpeed = mState.getFloat("speed", 0));
        mPaceNotifier.addListener(mSpeedNotifier);
        
        mCaloriesNotifier = new CaloriesNotifier(mCaloriesListener, mPedometerSettings, mUtils);
        mCaloriesNotifier.setCalories(mCalories = mState.getFloat("calories", 0));
        mStepDetector.addStepListener(mCaloriesNotifier);
        
        mSpeakingTimer = new SpeakingTimer(mPedometerSettings, mUtils);
        mSpeakingTimer.addListener(mStepDisplayer);
        mSpeakingTimer.addListener(mPaceNotifier);
        mSpeakingTimer.addListener(mDistanceNotifier);
        mSpeakingTimer.addListener(mSpeedNotifier);
        mSpeakingTimer.addListener(mCaloriesNotifier);
        mStepDetector.addStepListener(mSpeakingTimer);
        
        // Used when debugging:
        // mStepBuzzer = new StepBuzzer(this);
        // mStepDetector.addStepListener(mStepBuzzer);

        // Start voice
        reloadSettings();

        // Tell the user we started.
        Toast.makeText(this, getText(R.string.started), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "[SERVICE] onStartCommand");
        // Ask the system to restart counting if it kills the service.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "[SERVICE] onDestroy");
        mUtils.shutdownTTS();

        // Unregister our receiver.
        unregisterReceiver(mReceiver);
        unregisterDetector();
        
        saveState();

        stopForeground(true);

        wakeLock.release();
        
        super.onDestroy();
        
        // Stop detecting
        mSensorManager.unregisterListener(mStepDetector);

        // Tell the user we stopped.
        Toast.makeText(this, getText(R.string.stopped), Toast.LENGTH_SHORT).show();
    }

    /**
     * Writes the current totals to storage. Called on shutdown and every
     * few steps, so a killed process loses at most a handful of steps.
     */
    private void saveState() {
        mStateEditor = mState.edit();
        mStateEditor.putInt("steps", mSteps);
        mStateEditor.putInt("pace", mPace);
        mStateEditor.putFloat("distance", mDistance);
        mStateEditor.putFloat("speed", mSpeed);
        mStateEditor.putFloat("calories", mCalories);
        mStateEditor.putString(KEY_DAY, mDay);
        mStateEditor.apply();
    }

    /**
     * On start-up: if the stored totals are from an earlier day, move them
     * into History and start today from zero.
     */
    private void archiveStoredDayIfOld() {
        String today = LocalDate.now().toString();
        String storedDay = mState.getString(KEY_DAY, today);
        if (!storedDay.equals(today)) {
            History.saveDay(this, storedDay, mState.getInt("steps", 0),
                    mState.getFloat("calories", 0));
            mState.edit()
                    .putInt("steps", 0)
                    .putInt("pace", 0)
                    .putFloat("distance", 0)
                    .putFloat("speed", 0)
                    .putFloat("calories", 0)
                    .apply();
        }
        mDay = today;
        mState.edit().putString(KEY_DAY, mDay).apply();
    }

    /** While running: at the first step (or app visit) after midnight, archive and reset. */
    private void checkDayRollover() {
        String today = LocalDate.now().toString();
        if (mDay != null && !today.equals(mDay) && mStepDisplayer != null) {
            Log.i(TAG, "New day: archiving " + mSteps + " steps for " + mDay);
            History.saveDay(this, mDay, mSteps, mCalories);
            mDay = today;
            resetValues();
            saveState();
        }
    }

    private final StepListener mDayWatcher = new StepListener() {
        public void onStep() {
            checkDayRollover();
        }
        public void passValue() {
        }
    };

    /** Forwards steps from the built-in step detector chip. */
    private final SensorEventListener mHardwareStepListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            // TYPE_STEP_DETECTOR fires once per step.
            mStepDetector.onExternalStep();
        }
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private boolean hasActivityRecognitionPermission() {
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                    == PackageManager.PERMISSION_GRANTED;
    }

    /** True when steps come from the phone's step sensor chip. */
    public boolean isUsingHardwareSensor() {
        return mUsingHardwareSensor;
    }

    /** Re-picks the step source, e.g. after the user grants permission or changes the setting. */
    public void restartDetector() {
        unregisterDetector();
        registerDetector();
    }

    private void registerDetector() {
        // Prefer the phone's step detector chip: more accurate, far less battery.
        Sensor stepSensor = null;
        if (mPedometerSettings.useHardwareSensor() && hasActivityRecognitionPermission()) {
            stepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        }
        if (stepSensor != null) {
            mSensor = stepSensor;
            mUsingHardwareSensor = true;
            mSensorManager.registerListener(mHardwareStepListener, stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, 0);
            Log.i(TAG, "Counting with the built-in step detector");
            return;
        }

        // Fall back to the original accelerometer algorithm.
        mUsingHardwareSensor = false;
        Log.i(TAG, "Counting with the accelerometer");
        mSensor = mSensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER /*| 
            Sensor.TYPE_MAGNETIC_FIELD | 
            Sensor.TYPE_ORIENTATION*/);
        mSensorManager.registerListener(mStepDetector,
            mSensor,
            SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void unregisterDetector() {
        mSensorManager.unregisterListener(mStepDetector);
        mSensorManager.unregisterListener(mHardwareStepListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "[SERVICE] onBind");
        return mBinder;
    }

    /**
     * Receives messages from activity.
     */
    private final IBinder mBinder = new StepBinder();

    public interface ICallback {
        public void stepsChanged(int value);
        public void paceChanged(int value);
        public void distanceChanged(float value);
        public void speedChanged(float value);
        public void caloriesChanged(float value);
    }
    
    private ICallback mCallback;

    public void registerCallback(ICallback cb) {
        mCallback = cb;
        //mStepDisplayer.passValue();
        //mPaceListener.passValue();
    }
    
    private int mDesiredPace;
    private float mDesiredSpeed;
    
    /**
     * Called by activity to pass the desired pace value, 
     * whenever it is modified by the user.
     * @param desiredPace
     */
    public void setDesiredPace(int desiredPace) {
        mDesiredPace = desiredPace;
        if (mPaceNotifier != null) {
            mPaceNotifier.setDesiredPace(mDesiredPace);
        }
    }
    /**
     * Called by activity to pass the desired speed value, 
     * whenever it is modified by the user.
     * @param desiredSpeed
     */
    public void setDesiredSpeed(float desiredSpeed) {
        mDesiredSpeed = desiredSpeed;
        if (mSpeedNotifier != null) {
            mSpeedNotifier.setDesiredSpeed(mDesiredSpeed);
        }
    }
    
    public void reloadSettings() {
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        checkDayRollover();
        // Switch step source if the "use phone's step sensor" setting changed.
        if (mSensorManager != null && mPedometerSettings != null
                && mPedometerSettings.useHardwareSensor() != mUsingHardwareSensor) {
            restartDetector();
        }
        
        if (mStepDetector != null) { 
            mStepDetector.setSensitivity(
                    Float.valueOf(mSettings.getString("sensitivity", "10"))
            );
        }
        
        if (mStepDisplayer    != null) mStepDisplayer.reloadSettings();
        if (mPaceNotifier     != null) mPaceNotifier.reloadSettings();
        if (mDistanceNotifier != null) mDistanceNotifier.reloadSettings();
        if (mSpeedNotifier    != null) mSpeedNotifier.reloadSettings();
        if (mCaloriesNotifier != null) mCaloriesNotifier.reloadSettings();
        if (mSpeakingTimer    != null) mSpeakingTimer.reloadSettings();
    }
    
    public void resetValues() {
        mStepDisplayer.setSteps(0);
        mPaceNotifier.setPace(0);
        mDistanceNotifier.setDistance(0);
        mSpeedNotifier.setSpeed(0);
        mCaloriesNotifier.setCalories(0);
    }
    
    /**
     * Forwards pace values from PaceNotifier to the activity. 
     */
    private StepDisplayer.Listener mStepListener = new StepDisplayer.Listener() {
        public void stepsChanged(int value) {
            mSteps = value;
            if (mSteps % SAVE_EVERY_N_STEPS == 0) {
                saveState();
            }
            passValue();
        }
        public void passValue() {
            if (mCallback != null) {
                mCallback.stepsChanged(mSteps);
            }
        }
    };
    /**
     * Forwards pace values from PaceNotifier to the activity. 
     */
    private PaceNotifier.Listener mPaceListener = new PaceNotifier.Listener() {
        public void paceChanged(int value) {
            mPace = value;
            passValue();
        }
        public void passValue() {
            if (mCallback != null) {
                mCallback.paceChanged(mPace);
            }
        }
    };
    /**
     * Forwards distance values from DistanceNotifier to the activity. 
     */
    private DistanceNotifier.Listener mDistanceListener = new DistanceNotifier.Listener() {
        public void valueChanged(float value) {
            mDistance = value;
            passValue();
        }
        public void passValue() {
            if (mCallback != null) {
                mCallback.distanceChanged(mDistance);
            }
        }
    };
    /**
     * Forwards speed values from SpeedNotifier to the activity. 
     */
    private SpeedNotifier.Listener mSpeedListener = new SpeedNotifier.Listener() {
        public void valueChanged(float value) {
            mSpeed = value;
            passValue();
        }
        public void passValue() {
            if (mCallback != null) {
                mCallback.speedChanged(mSpeed);
            }
        }
    };
    /**
     * Forwards calories values from CaloriesNotifier to the activity. 
     */
    private CaloriesNotifier.Listener mCaloriesListener = new CaloriesNotifier.Listener() {
        public void valueChanged(float value) {
            mCalories = value;
            passValue();
        }
        public void passValue() {
            if (mCallback != null) {
                mCallback.caloriesChanged(mCalories);
            }
        }
    };
    
    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        CharSequence text = getText(R.string.app_name);

        Intent pedometerIntent = new Intent();
        pedometerIntent.setComponent(new ComponentName(this, Pedometer.class));
        pedometerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                pedometerIntent, piFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getText(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
            mNM.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(text)
                .setContentText(getText(R.string.notification_subtitle))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }


    // BroadcastReceiver for handling ACTION_SCREEN_OFF.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Check action just to be on the safe side.
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // Unregisters the listener and registers it again.
                StepService.this.unregisterDetector();
                StepService.this.registerDetector();
                if (mPedometerSettings.wakeAggressively()) {
                    wakeLock.release();
                    acquireWakeLock();
                }
            }
        }
    };

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        int wakeFlags;
        if (mPedometerSettings.wakeAggressively()) {
            wakeFlags = PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
        }
        else if (mPedometerSettings.keepScreenOn()) {
            wakeFlags = PowerManager.SCREEN_DIM_WAKE_LOCK;
        }
        else {
            wakeFlags = PowerManager.PARTIAL_WAKE_LOCK;
        }
        wakeLock = pm.newWakeLock(wakeFlags, TAG);
        wakeLock.acquire();
    }

}

