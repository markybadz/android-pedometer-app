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
    private static final int GOAL_NOTIFICATION_ID = 2;
    /** Update the notification's step count every this many steps. */
    private static final int NOTIFY_EVERY_N_STEPS = 10;
    /** State key: the day the step-goal notification was last shown. */
    private static final String KEY_GOAL_DAY = "goal_notified_day";
    private static final int SAVE_EVERY_N_STEPS = 20;
    /** State key: true while the user wants counting on (used to restart after reboot). */
    private static final String KEY_COUNTING = "counting_enabled";
    /** State key: the ISO date the current totals belong to. */
    private static final String KEY_DAY = "day";

    /** Whether counting should be running (it was started and not paused or quit). */
    public static boolean isCountingEnabled(Context context) {
        return context.getSharedPreferences("state", 0).getBoolean(KEY_COUNTING, false);
    }

    /** False until the user has started or paused counting at least once. */
    public static boolean hasCountingChoice(Context context) {
        return context.getSharedPreferences("state", 0).contains(KEY_COUNTING);
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
    /** True when the step-counter chip counts (no wake lock needed). */
    private boolean mUsingStepCounter;
    /** Last cumulative value from the step-counter chip, or -1 before the first reading. */
    private long mLastCounter = -1;
    /** State key: last step-counter value, to catch up after the service is killed or the phone reboots. */
    private static final String KEY_COUNTER = "step_counter_last";
    /** How long the step-counter chip may batch steps while the phone sleeps (5 minutes). */
    private static final int COUNTER_BATCH_US = 5 * 60 * 1000 * 1000;
    private int mLastSavedSteps;

    // Sharing with Health Connect (Google Health): steps are collected into
    // blocks of up to 15 minutes, then written as one record each.
    private static final long SHARE_BLOCK_MS = 15 * 60 * 1000;
    private static final String KEY_SHARE_START = "share_block_start";
    private static final String KEY_SHARE_STEPS = "share_block_steps";
    /** State key: when steps were last written to Health Connect (shown on the main screen). */
    public static final String KEY_LAST_SHARED = "share_last_written";
    private long mShareStart;
    private int mShareSteps;
    private int mLastNotifiedSteps;
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

        // Start detecting
        mStepDetector = new StepDetector();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLastCounter = mState.getLong(KEY_COUNTER, -1);
        mShareStart = mState.getLong(KEY_SHARE_START, 0);
        mShareSteps = mState.getInt(KEY_SHARE_STEPS, 0);
        registerDetector();

        // Register our receiver for the ACTION_SCREEN_OFF action. This will make our receiver
        // code be called whenever the phone enters standby mode.
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        // Must be the first listener, so a step taken just after midnight
        // is counted towards the new day.
        mStepDetector.addStepListener(mDayWatcher);
        mStepDetector.addStepListener(mShareFeed);

        mStepDisplayer = new StepDisplayer(mPedometerSettings, mUtils);
        mStepDisplayer.setSteps(mSteps = mState.getInt("steps", 0));
        mStepDisplayer.addListener(mStepListener);
        mStepDetector.addStepListener(mStepDisplayer);

        mPaceNotifier     = new PaceNotifier(mPedometerSettings, mUtils);
        mPaceNotifier.setPace(mPace = mState.getInt("pace", 0));
        mPaceNotifier.addListener(mPaceListener);
        // With the step-counter chip, steps can arrive in batches after the
        // phone wakes, which would make pace meaningless; pace then comes
        // from the step detector chip instead (see mHardwarePaceListener).
        mStepDetector.addStepListener(mPaceFeed);

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
        showNotification();
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
        
        flushSharedSteps();
        saveState();

        stopForeground(true);

        releaseWakeLock();
        // A user stop: don't count the steps walked while stopped on the next start.
        mState.edit().remove(KEY_COUNTER).apply();
        
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
        mStateEditor.putLong(KEY_SHARE_START, mShareStart);
        mStateEditor.putInt(KEY_SHARE_STEPS, mShareSteps);
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
            flushSharedSteps(); // keep each shared block inside one day
            History.saveDay(this, mDay, mSteps, mCalories);
            mDay = today;
            resetValues();
            saveState();
        }
    }

    /** Collects steps for Health Connect, writing a block every 15 minutes. */
    private final StepListener mShareFeed = new StepListener() {
        public void onStep() {
            if (mPedometerSettings == null || !mPedometerSettings.isSharingEnabled()) return;
            long now = System.currentTimeMillis();
            if (mShareSteps == 0) mShareStart = now;
            mShareSteps++;
            if (now - mShareStart >= SHARE_BLOCK_MS) {
                flushSharedSteps();
            }
        }
        public void passValue() {
        }
    };

    /** Writes the collected block of steps to Health Connect and starts a new one. */
    private void flushSharedSteps() {
        if (mShareSteps <= 0 || mPedometerSettings == null) return;
        final int steps = mShareSteps;
        long start = mShareStart;
        long end = System.currentTimeMillis();
        double meters = steps * mPedometerSettings.getStepLengthMeters();
        mShareSteps = 0;
        mShareStart = 0;
        if (!mPedometerSettings.isSharingEnabled()) return;
        WatchSteps.writeSteps(this, start, end, steps, meters, new WatchSteps.Callback<Boolean>() {
            public void onResult(Boolean written) {
                if (written) {
                    mState.edit().putLong(KEY_LAST_SHARED, System.currentTimeMillis()).apply();
                } else {
                    Log.i(TAG, "Not allowed to write steps to Health Connect");
                }
            }
            public void onError(String message) {
                Log.w(TAG, "Sharing " + steps + " steps failed: " + message);
            }
        });
    }

    private final StepListener mDayWatcher = new StepListener() {
        public void onStep() {
            checkDayRollover();
        }
        public void passValue() {
        }
    };

    /** Forwards steps from the built-in step detector chip (used when there's no counter chip). */
    private final SensorEventListener mHardwareStepListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            // TYPE_STEP_DETECTOR fires once per step.
            mStepDetector.onExternalStep();
        }
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    /**
     * Counts with the step-counter chip. It keeps counting in hardware while
     * the phone sleeps and reports a running total, so no wake lock is needed:
     * the difference from the last reading is the number of new steps.
     */
    private final SensorEventListener mCounterListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            long value = (long) event.values[0];
            long steps;
            if (mLastCounter < 0) {
                steps = 0;                       // first reading: just the baseline
            } else if (value < mLastCounter) {
                steps = value;                   // phone rebooted: chip restarted from 0
            } else {
                steps = value - mLastCounter;
            }
            mLastCounter = value;
            mState.edit().putLong(KEY_COUNTER, value).apply();
            if (steps > 0 && steps < 100000) {
                mStepDetector.onExternalSteps((int) steps);
            }
        }
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    /** Live pace from the step detector chip while the counter chip does the counting. */
    private final SensorEventListener mHardwarePaceListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (mPaceNotifier != null) mPaceNotifier.onStep();
        }
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    /** Passes counted steps to pace, except when pace comes from the detector chip. */
    private final StepListener mPaceFeed = new StepListener() {
        public void onStep() {
            if (!mUsingStepCounter && mPaceNotifier != null) mPaceNotifier.onStep();
        }
        public void passValue() {
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

    /** True when counting uses the step-counter chip, which needs no wake lock. */
    public boolean isUsingStepCounter() {
        return mUsingStepCounter;
    }

    private void registerDetector() {
        mUsingStepCounter = false;
        boolean hardwareAllowed = mPedometerSettings.useHardwareSensor()
                && hasActivityRecognitionPermission();

        // Best: the step-counter chip. Prefer its wake-up version, which
        // wakes the phone at most every few minutes to deliver a batch.
        Sensor counter = null;
        if (hardwareAllowed) {
            counter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true);
            if (counter == null) {
                counter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            }
        }
        if (counter != null) {
            mSensor = counter;
            mUsingHardwareSensor = true;
            mUsingStepCounter = true;
            mSensorManager.registerListener(mCounterListener, counter,
                    SensorManager.SENSOR_DELAY_NORMAL, COUNTER_BATCH_US);
            Sensor detector = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (detector != null) {
                mSensorManager.registerListener(mHardwarePaceListener, detector,
                        SensorManager.SENSOR_DELAY_NORMAL, 0);
            }
            Log.i(TAG, "Counting with the step-counter chip (no wake lock)");
            updateWakeLock();
            return;
        }

        // Next: the step detector chip (needs the CPU awake to catch each step).
        Sensor stepSensor = null;
        if (hardwareAllowed) {
            stepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        }
        if (stepSensor != null) {
            mSensor = stepSensor;
            mUsingHardwareSensor = true;
            mSensorManager.registerListener(mHardwareStepListener, stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, 0);
            Log.i(TAG, "Counting with the built-in step detector");
            updateWakeLock();
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
        updateWakeLock();
    }

    private void unregisterDetector() {
        mSensorManager.unregisterListener(mStepDetector);
        mSensorManager.unregisterListener(mHardwareStepListener);
        mSensorManager.unregisterListener(mCounterListener);
        mSensorManager.unregisterListener(mHardwarePaceListener);
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
            // Steps can arrive in batches, so compare with the last save
            // rather than relying on exact multiples.
            if (mSteps < mLastSavedSteps || mSteps - mLastSavedSteps >= SAVE_EVERY_N_STEPS) {
                mLastSavedSteps = mSteps;
                saveState();
            }
            if (mSteps < mLastNotifiedSteps || mSteps - mLastNotifiedSteps >= NOTIFY_EVERY_N_STEPS) {
                mLastNotifiedSteps = mSteps;
                updateNotification();
            }
            checkStepGoal();
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
    /** Builds the ongoing notification, with today's step count once there is one. */
    private Notification buildNotification() {
        Intent pedometerIntent = new Intent();
        pedometerIntent.setComponent(new ComponentName(this, Pedometer.class));
        pedometerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                pedometerIntent, PendingIntent.FLAG_IMMUTABLE);

        CharSequence text = mSteps > 0
                ? getString(R.string.notification_steps, String.format(java.util.Locale.getDefault(), "%,d", mSteps))
                : getText(R.string.notification_subtitle);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getText(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        mNM.createNotificationChannel(channel);
    }

    /**
     * Puts the service in the foreground. Called from onCreate and again from
     * every onStartCommand, because each startForegroundService() call must be
     * answered with startForeground(), even if the service was already running.
     */
    private void showNotification() {
        createChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /** Refreshes the step count shown in the ongoing notification. */
    private void updateNotification() {
        if (mNM != null) {
            mNM.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /** One quiet notification per day when the step goal is reached. */
    private void checkStepGoal() {
        if (mPedometerSettings == null || mDay == null) return;
        int goal = mPedometerSettings.getStepGoal();
        if (goal <= 0 || mSteps < goal) return;
        if (mDay.equals(mState.getString(KEY_GOAL_DAY, ""))) return;
        mState.edit().putString(KEY_GOAL_DAY, mDay).apply();

        Intent intent = new Intent(this, Pedometer.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE);
        mNM.notify(GOAL_NOTIFICATION_ID, new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getText(R.string.goal_reached_title))
                .setContentText(getString(R.string.goal_reached_text,
                        String.format(java.util.Locale.getDefault(), "%,d", goal)))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }


    // BroadcastReceiver for handling ACTION_SCREEN_OFF.
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Check action just to be on the safe side.
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // The step-counter chip keeps counting while asleep; nothing to do.
                if (mUsingStepCounter && !mPedometerSettings.wakeAggressively()) {
                    return;
                }
                // Older phones stop sensors when the screen goes off: re-register.
                StepService.this.unregisterDetector();
                StepService.this.registerDetector();
                if (mPedometerSettings.wakeAggressively()) {
                    releaseWakeLock();
                    acquireWakeLock();
                }
            }
        }
    };

    /**
     * Holds a wake lock only when it's needed: for the accelerometer and
     * step-detector methods (which need the CPU awake), or when the user
     * chose "Screen on" / "Aggressive" in Settings.
     */
    private void updateWakeLock() {
        boolean needed = !mUsingStepCounter
                || mPedometerSettings.keepScreenOn()
                || mPedometerSettings.wakeAggressively();
        if (needed) {
            if (wakeLock == null || !wakeLock.isHeld()) acquireWakeLock();
        } else {
            releaseWakeLock();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

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

