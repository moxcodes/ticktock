package com.example.mox.tick;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static android.view.View.FOCUS_DOWN;

public class watcherService extends Service implements SensorEventListener{
    boolean initializedSensors;

    float[] accelerometerVal = new float[3];
    float[] magnetometerVal = new float[3];
    float[] gravitometerVal = new float[3];
    float[] gyroscopeVal = new float[3];
    float pedometerVal;
    float photometerVal;
    UsageStatsManager usm;
    MediaSessionManager msm;
    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;
    SensorManager mSensorManager;



    Boolean interruptedSleep;
    String fullLog = "";
    private final IBinder mBinder = new LocalBinder();

    Location[] recentLoc = new Location[3];
    Thread watcherThread;

    public watcherService(){
    }


    @Override
    public void onCreate(){
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
        watcherThread = new Thread(runWatch);
        watcherThread.start();
        initializedSensors=false;
        usm = (UsageStatsManager) this.getSystemService(Context.USAGE_STATS_SERVICE);
        msm = (MediaSessionManager) this.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }


    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }




    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()){
            case Sensor.TYPE_ACCELEROMETER:
                accelerometerVal=event.values;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetometerVal=event.values;
                break;
            case Sensor.TYPE_GRAVITY:
                gravitometerVal=event.values;
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroscopeVal=event.values;
                break;
            case Sensor.TYPE_STEP_COUNTER:
                pedometerVal=event.values[0];
                break;
            case Sensor.TYPE_LIGHT:
                photometerVal=event.values[0];
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    public class LocalBinder extends Binder {
        watcherService getService() {
            return watcherService.this;
        }
    }

    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // The PendingIntent to launch our activity if the user selects this notification
        // Set the info for the views that show in the notification panel.
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_remove_red_eye_black_24dp)  // the status icon
                .setTicker(text)  // the status text
                .setWhen(System.currentTimeMillis())  // the time stamp
                .setContentTitle(getText(R.string.local_service_label))  // the label of the entry
                .setContentText(text)  // the contents of the entry
                .build();

        // Send the notification.
//        mNM.notify(NOTIFICATION, notification);
    }


    protected Runnable runWatch = new Runnable() {
        public void run() {
            List<Sensor> deviceSensors;
            Sensor accelerometer;
            Sensor magnetometer;
            Sensor gravitometer;
            Sensor gyroscope;
            Sensor pedometer;
            Sensor photometer;
            String newLog = "";
            String activeApp = " : Active : None \n";

            SimpleDateFormat minsdf = new SimpleDateFormat("yyyyMMdd_HHmm");
            String filedate = minsdf.format(new Date());

            SimpleDateFormat hoursdf = new SimpleDateFormat("yyyyMMdd_HH");
            String hourdate = hoursdf.format(new Date());

            SimpleDateFormat daysdf = new SimpleDateFormat("yyyyMMdd");
            String daydate = daysdf.format(new Date());

            long prevTime = System.currentTimeMillis();
            long newTime;
            while (fullLog.length() < 10000000) {
                newLog = "";
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                if (mSensorManager != null && !initializedSensors) {
                    accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                    magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                    gravitometer = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                    gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                    pedometer = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                    photometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
                    mSensorManager.registerListener(watcherService.this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                    mSensorManager.registerListener(watcherService.this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
                    mSensorManager.registerListener(watcherService.this, gravitometer, SensorManager.SENSOR_DELAY_NORMAL);
                    mSensorManager.registerListener(watcherService.this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
                    mSensorManager.registerListener(watcherService.this, pedometer, SensorManager.SENSOR_DELAY_NORMAL);
                    mSensorManager.registerListener(watcherService.this, photometer, SensorManager.SENSOR_DELAY_NORMAL);
                    deviceSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
/*                    for (int i = 0; i < deviceSensors.size(); i++) {
                        newLog += deviceSensors.get(i).getName() + " ";
                        newLog += deviceSensors.get(i).getStringType() + "\n";
                    }*/
                    initializedSensors = true;
                }
                Calendar calendar = Calendar.getInstance();
                long endTime = calendar.getTimeInMillis();
                calendar.add(Calendar.SECOND, -5);
                long startTime = calendar.getTimeInMillis();
                UsageEvents uEvents = usm.queryEvents(startTime, endTime);
                // For now, record only a currently active app
                while (uEvents.hasNextEvent()) {
                    UsageEvents.Event e = new UsageEvents.Event();
                    uEvents.getNextEvent(e);
                    if (e != null) {
                        if (e.getEventType() == e.MOVE_TO_BACKGROUND){
                            //Log.d("<o>",e.getPackageName() + " TO BG");
                            activeApp = " : Active : None \n";}
                        if (e.getEventType() == e.MOVE_TO_FOREGROUND){
                           // Log.d("<o>",e.getPackageName() + " TO FG");
                            activeApp = " : Active : " + e.getPackageName() +  "\n";}
                    }
                }
                newLog += sdf.format(new Date()) + activeApp;
                if (ContextCompat.checkSelfPermission(watcherService.this,
                        Manifest.permission.MEDIA_CONTENT_CONTROL)
                        == PackageManager.PERMISSION_GRANTED) {
                    List<MediaController> mC = msm.getActiveSessions(null);
                    for (int j = 0; j < mC.size(); j++) {
                        MediaController tempMC = mC.get(j);
                        try {
                            if (tempMC != null && tempMC.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {

                                newLog += sdf.format(new Date()) + " : Playback : "
                                        + tempMC.getMetadata().getDescription().getSubtitle() + " | "
                                        + tempMC.getMetadata().getDescription().getTitle() + "\n";

                            }
                        } catch (NullPointerException e) {
                        }
                    }
                }
                if (mSensorManager != null && initializedSensors) {
                    newLog += sdf.format(new Date()) +
                            " : acc : " + Float.toString(accelerometerVal[0]) +
                            " , " + Float.toString(accelerometerVal[1]) +
                            " , " + Float.toString(accelerometerVal[2]) + "\n";
                    newLog += sdf.format(new Date()) +
                            " : mag : " + Float.toString(magnetometerVal[0]) +
                            " , " + Float.toString(magnetometerVal[1]) +
                            " , " + Float.toString(magnetometerVal[2]) + "\n";
                    newLog += sdf.format(new Date()) +
                            " : grv : " + Float.toString(gravitometerVal[0]) +
                            " , " + Float.toString(gravitometerVal[1]) +
                            " , " + Float.toString(gravitometerVal[2]) + "\n";
                    newLog += sdf.format(new Date()) +
                            " : gyr : " + Float.toString(gyroscopeVal[0]) +
                            " , " + Float.toString(gyroscopeVal[1]) +
                            " , " + Float.toString(gyroscopeVal[2]) + "\n";
                    newLog += sdf.format(new Date()) +
                            " : stp : " + Float.toString(pedometerVal) + "\n";
                    newLog += sdf.format(new Date()) +
                            " : pht : " + Float.toString(photometerVal) + "\n";
                }

                //append all harvested information to the running log


                if (recentLoc[2] != null) {
                    newLog += sdf.format(new Date()) + " : loc : "
                            + String.valueOf(recentLoc[2].getLatitude()) + " | "
                            + String.valueOf(recentLoc[2].getLongitude()) + " | "
                            + String.valueOf(recentLoc[2].getAltitude()) + " | "
                            + String.valueOf(recentLoc[2].getAccuracy()) + "\n";
                } else {
                    newLog = sdf.format(new Date()) + " : loc :  no location \n";
                }
                final String updateString = newLog;
                fullLog += updateString;
                // 5-minute files, 1-hour subdirectories, 1-day directories

                newTime=System.currentTimeMillis();
                if(newTime/(5*60*1000)-prevTime/(5*60*1000) > 0 ) {
                    // Okay, to make this work for now, I'm just going to hardcode this.
                    // It should be set in the app.
                    // TODO: Develop remote sync options
                    //      Rough idea list:
                    //          - Owncloud (that's what I'd use)
                    //          - Dropbox
                    //          - Google drive
                    //          - sftp (?)
                    String rootDir = "/sdcard/Log";
                    File directory = new File(rootDir + File.separator + daydate);
                    directory.mkdirs();
                    directory = new File(rootDir + File.separator + daydate
                                                 + File.separator + hourdate);
                    directory.mkdirs();
                    File outputFile = new File(rootDir + File.separator + daydate
                                                        + File.separator + hourdate
                                                        + File.separator + filedate);
                    FileOutputStream outputStream;
                    try {
                        outputStream = new FileOutputStream(outputFile);
                        outputStream.write(fullLog.getBytes());
                        outputStream.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    fullLog = "";
                }
                int i = 0;
                interruptedSleep = false;
                while (!interruptedSleep && i < 1) {
                    try {
                        watcherThread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    i++;
                }
                if(newTime/(5*60*1000)-prevTime/(5*60*1000) > 0 ) {
                    prevTime=System.currentTimeMillis();
                    filedate = minsdf.format(new Date());
                    hourdate = hoursdf.format(new Date());
                    daydate = daysdf.format(new Date());
                }
            }
        }
    };


    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            recentLoc[0] = recentLoc[1];
            recentLoc[1] = recentLoc[2];
            recentLoc[2] = location;
            interruptedSleep=true;
        }


        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {}
    };

}
