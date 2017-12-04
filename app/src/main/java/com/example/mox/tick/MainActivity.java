package com.example.mox.tick;

import android.Manifest;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.provider.Settings;
import android.renderscript.ScriptGroup;
import android.support.annotation.NonNull;
import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import android.support.v7.app.AppCompatActivity;
import static android.view.View.FOCUS_DOWN;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
                                                                GoogleApiClient.OnConnectionFailedListener {
    private watcherService theWatcher;
    String fullLog = "";

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private SensorManager mSensorManager;
    private Thread scribeThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        int MY_PERMISSIONS_REQUEST_LOCATION=1;
        int MY_PERMISSIONS_REQUEST_STORAGE=1;


        ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.LTGRAY));
        actionBar.show();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_STORAGE);
        }

        //Check if permission enabled
//        if (UStats.getUsageStatsList(this).isEmpty()){
//            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
  //          startActivity(intent);
  //      }




        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,
                        this /* OnConnectionFailedListener */)
                .addApi(LocationServices.API).addConnectionCallbacks(this).build();
        mGoogleApiClient.connect();
        scribeThread = new Thread(logWrite);
        scribeThread.start();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.actionbar, menu);
        return true;
    }


    protected Runnable logWrite = new Runnable() {
        public void run() {
            final TextView dataLog = (TextView)findViewById(R.id.DataWindow);
            String currentText = "";
            while(true) {
                if (theWatcher != null) {
                    if (currentText != theWatcher.fullLog) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dataLog.setText(theWatcher.fullLog);
                            }
                        });

                        currentText = theWatcher.fullLog;
                    }
                    if(((CheckBox)findViewById(R.id.checkBox)).isChecked())
                    {
                        final ScrollView scrollw = (ScrollView)findViewById(R.id.ScrollWindow);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                scrollw.fullScroll(FOCUS_DOWN);
                            }});
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };


    @Override
    public void onConnectionFailed(ConnectionResult conn)
    {
        fullLog+="Well that's a bummer...\n";
    }
    @Override
    public void onConnected(Bundle bun)
    {

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(500);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        /* Okay, we're going to be honest here. I have no idea what the hell is supposed to
            be going on with these services. Apparently the typical way of going about this
            is to start them and bind them and some sort of bullshit.*/
        Log.d("<o>",Boolean.toString(mSensorManager == null));
        Intent i = new Intent(this, watcherService.class);
        this.startService(i);
        this.bindService(new Intent(this,watcherService.class),mConnection,this.BIND_AUTO_CREATE);
        // There is no good reason that this should work
        //  This service crap is (a) confusing as balls (b) probably works as well as I understand it : not very

    }

    @Override
    public void onConnectionSuspended(int num)
    {
        fullLog+="Location connection suspended";
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            theWatcher = ((watcherService.LocalBinder)service).getService();

            // Tell the user about this for our demo.
            Toast.makeText(MainActivity.this, R.string.local_service_connected,
                    Toast.LENGTH_SHORT).show();
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest,theWatcher.locationListener);
            Log.d("<o>",Boolean.toString(mSensorManager == null));

            theWatcher.mSensorManager = mSensorManager;
            CharSequence text = getText(R.string.local_service_started);

            Notification notification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.drawable.ic_remove_red_eye_black_24dp)  // the status icon
                    .setTicker(text)  // the status text
                    .setWhen(System.currentTimeMillis())  // the time stamp
                    .setContentTitle(getText(R.string.local_service_label))  // the label of the entry
                    .setContentText(text)  // the contents of the entry
                    .build();
            Log.d("<o>","watcher pushed to foreground");
            theWatcher.startForeground(6001243,notification);
        }

        public void onServiceDisconnected(ComponentName className) {
            theWatcher = null;
            Toast.makeText(MainActivity.this, R.string.local_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        }
    };
}
