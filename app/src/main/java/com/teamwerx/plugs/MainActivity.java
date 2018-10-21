package com.teamwerx.plugs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.teamwerx.plugs.Services.CameraHelper;
import com.teamwerx.plugs.Services.UploadHelper;
import com.teamwerx.plugs.data.Preferences;
import com.teamwerx.plugs.image.ImageProcessing;
import com.teamwerx.plugs.motion_detection.IMotionDetection;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements IMqttActionListener, MqttCallback,
        SensorEventListener, LocationListener {

    MqttAndroidClient mClient;

    private float lastX, lastY, lastZ;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private float mVibrateThreshold = 0;
    public Vibrator mVibrator;

    SurfaceView mCameraPreview;
    ImageView mSurfaceView;

    public final static String TAG = "PLUGSAPP";

    String mServerHostName;

    final int SERVER_HOST_PORT = 8050;

    private CameraHelper mCamera;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    int TAKE_PHOTO_CODE = 0;
    public static int count = 110;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1; // 1 minute

    private static final long MOTION_INTERVAL = 7;

    private Location mLastLocation;
    private LocationManager mLocationManager;
    private String mDeviceId;

    ProgressBar mProgressBar;
    TextView mProgressStatus;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences prefs = this.getPreferences(Context.MODE_PRIVATE);
        mServerHostName = prefs.getString(SERVER_KEY, "");
        mDeviceId = prefs.getString(DEVICE_ID_KEY, "");

        verifyAppPermissions(this);

        mCameraPreview = findViewById(R.id.cameraPreview);
        mSurfaceView = findViewById(R.id.motionDetectorPreview);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            mVibrateThreshold = mAccelerometer.getMaximumRange() / 2;

        } else {
            Log.d(TAG, "Sorry, no accelerator");
        }

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES,
                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

        mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        sendLocationInfo(mLastLocation);

        mProgressBar = findViewById(R.id.uploadProgress);
        mProgressStatus = findViewById(R.id.uploadStatus);
        mProgressBar.setVisibility(View.GONE);
        mVibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        Log.d(TAG, "App Startup");
    }

    boolean mHasStoragePermissions = false;
    boolean mHasGPSPermissions = false;
    boolean mHasCemeraPermissions = false;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] APP_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public void verifyAppPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED || true) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    APP_PERMISSIONS,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
        else
        {
            mHasStoragePermissions = true;
        }
    }

    private boolean mIsMQTTConnected = false;
    private boolean mIsCameraActive = false;

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        boolean settingsReady = mServerHostName != null &&
                                mServerHostName.length() > 0 &&
                                mDeviceId != null &&
                                mDeviceId.length() > 0;

        menu.findItem(R.id.action_mqtt_connect).setEnabled(!mIsMQTTConnected && settingsReady);
        menu.findItem(R.id.action_mqtt_disconnect).setEnabled(mIsMQTTConnected && settingsReady);
        menu.findItem(R.id.action_camera_start).setEnabled(!mIsCameraActive);
        menu.findItem(R.id.action_camera_stop).setEnabled(mIsCameraActive);
        menu.findItem(R.id.action_take_photo).setEnabled(mIsCameraActive && settingsReady);
        menu.findItem(R.id.action_send_location).setEnabled(mIsMQTTConnected && settingsReady);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch(id)
        {
            case R.id.action_camera_start: startCamera(); break;
            case R.id.action_camera_stop: stopCamera(); break;
            case R.id.action_mqtt_connect: connectToMQTT(); break;
            case R.id.action_mqtt_disconnect: disconnectFromMQTT(); break;
            case R.id.action_take_photo: takePhoto(); break;
            case R.id.action_send_location: sendCurrentLocation(); break;
            case R.id.action_settings: showSettingsDialog(); break;
            default: super.onOptionsItemSelected(item);
        }

        // If the default case was not executed (i.e. menu was handled, return true)
        return true;
    }

    private void disconnectFromMQTT() {
        if(mClient != null){
            mClient.close();
            mClient = null;
            mIsMQTTConnected = false;
            invalidateOptionsMenu();
        }
    }

    private void connectToMQTT(){
        Log.d(TAG, "Connecting to MQTT");

        String clientId = MqttClient.generateClientId();
         mClient = new MqttAndroidClient(this.getApplicationContext(),
                 "tcp://" + mServerHostName + ":1883",
                        clientId);
         mClient.setCallback(this);

        try {
            MqttConnectOptions options = new MqttConnectOptions();

            options.setUserName("kevinw");
            options.setPassword("Test1234".toCharArray());

            //IMqttToken token = mClient.connect(options);
            IMqttToken token = mClient.connect();
            token.setActionCallback(this);

        } catch (MqttException e) {
            Log.d(TAG, "MQTT EXCPETION");
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    private void startCamera() {
        if(mCamera == null) {
            mCamera = new CameraHelper(this, mCameraPreview);
            mCamera.open();
            mCameraPreview.setVisibility(View.VISIBLE);
            mIsCameraActive = true;
            invalidateOptionsMenu();
         }
    }

    private void stopCamera() {
        if(mCamera != null) {
            mCamera.close();
            mIsCameraActive = false;
            mCamera = null;
            invalidateOptionsMenu();
        }

        mCameraPreview.setVisibility(View.INVISIBLE);
    }

    @SuppressLint("MissingPermission")
    private void sendCurrentLocation() {
        mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(mLastLocation != null) {
            sendLocationInfo(mLastLocation);
        }
        else {
            Toast.makeText(this, "Sorry location information is not available", Toast.LENGTH_SHORT);
        }
    }

    final String SERVER_KEY = "SERVER_SETTING";
    final String DEVICE_ID_KEY = "DEVICE_ID_SETTING";

    private void showSettingsDialog() {
        LayoutInflater inflater = this.getLayoutInflater();

        View content = inflater.inflate(R.layout.settings_dlg_view, null);

        final SharedPreferences prefs = this.getPreferences(Context.MODE_PRIVATE);

        final EditText srvr = content.findViewById(R.id.settings_server);
        final EditText deviceId = content.findViewById(R.id.settings_device_id);
        srvr.setText(mServerHostName);
        deviceId.setText(mDeviceId);;
        AlertDialog.Builder dlgBldr = new AlertDialog.Builder(this);
        dlgBldr.setTitle(R.string.app_name);
        dlgBldr.setView(content);

        dlgBldr.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dlg, int which){
                mServerHostName = srvr.getText().toString();
                mDeviceId = deviceId.getText().toString();

                prefs.edit().putString(SERVER_KEY, mServerHostName).commit();
                prefs.edit().putString(DEVICE_ID_KEY, mDeviceId).commit();
                invalidateOptionsMenu();
            }
        });

        dlgBldr.show();
    }

    private void startRecordingAudio() {


    }

    private void stopRecordingAudio() {

    }

    private void takePhoto() {
        if(mCameraPreview.getVisibility() == View.VISIBLE &&
                mCamera != null && mIsCameraActive) {
            Bitmap original = mCamera.getCurrentBitmap();
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(original, 480, 640, false);
            final String dir = Environment.getExternalStorageDirectory() + "/AppPicFolder/";
            Log.d(TAG, dir);

            //TODO: Need to ensure we have all file permissions

            File newdir = new File(dir);
            newdir.mkdirs();

            Calendar current = Calendar.getInstance();
            current.set(Calendar.ZONE_OFFSET, 0);

            String fileName = String.format("%s_%04d%02d%02d%02d%02d%02d.jpg",
                    mDeviceId,
                    current.get(Calendar.YEAR),
                    current.get(Calendar.MONTH),
                    current.get(Calendar.DAY_OF_MONTH),
                    current.get(Calendar.HOUR_OF_DAY),
                    current.get(Calendar.MINUTE),
                    current.get(Calendar.SECOND));

            String fullFileName = String.format("%s%s", dir, fileName);
            Log.d(TAG, "Full File Name: " + fullFileName);

            try (FileOutputStream out = new FileOutputStream(fullFileName)) {
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
            } catch (IOException e) {
                Log.d(TAG, "Exception saving photo");
                Log.d(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }

            UploadHelper uploader = new UploadHelper(mProgressBar, mProgressStatus, mDeviceId, mServerHostName, SERVER_HOST_PORT);
            uploader.setMedia(fullFileName);
            uploader.execute();
        }
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "onSuccess");
        try {
            mClient.subscribe("incoming/dev001/+",0);
            invalidateOptionsMenu();
            mIsMQTTConnected = true;

            if(mLastLocation != null) {
                sendLocationInfo(mLastLocation);
            }
        } catch (MqttException e) {
            Log.d(TAG, "MQTT EXCEPTION");
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        // Something went wrong e.g. connection timeout or firewall problems
        Log.d(TAG, "onFailure");
        Log.d(TAG, exception.getCause().getMessage());
        Log.d(TAG, "onFailure");

        Toast.makeText(this, "Could not connect to MQTT Server", Toast.LENGTH_SHORT);
        Toast.makeText(this, exception.getCause().getMessage(), Toast.LENGTH_LONG);
        invalidateOptionsMenu();
    }

    @Override
    public void connectionLost(Throwable cause){
        Log.d(TAG, "MQTT Server connection lost" + cause.getMessage());
        mIsMQTTConnected = false;
        invalidateOptionsMenu();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message){
        Log.d(TAG, "Message arrived:" + topic + ":" + message.toString());

        if(topic.contains("startcamera")){
            startCamera();
        }
        else if(topic.contains("stopcamera")) {
            stopCamera();
        }
        else if(topic.contains("takephoto")) {
            takePhoto();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token){
        Log.d(TAG, "Delivery complete");
    }

    Calendar lastMotion = null;

    @Override
    public void onSensorChanged(SensorEvent event) {
        deltaX = Math.abs(lastX - event.values[0]);
        deltaY = Math.abs(lastY - event.values[1]);
        deltaZ = Math.abs(lastZ - event.values[2]);

        lastX = event.values[0];
        lastY = event.values[1];
        lastZ = event.values[2];

        if(mClient != null && mIsMQTTConnected) {
            if (deltaX > 0.1 || deltaY > 0.1 || deltaZ > 0.1)
            {
                if(lastMotion != null)
                {
                    if((Calendar.getInstance().getTime().getTime() - lastMotion.getTime().getTime()) / 1000 < MOTION_INTERVAL)
                    {
                        return;
                    }
                }

                lastMotion = Calendar.getInstance();


                try {
                    mClient.publish(String.format("plugs/%s/vibration", mDeviceId), "{vibration:true}".getBytes(), 0, false);
                } catch (MqttException e) {
                    e.printStackTrace();
                }

                Log.d(TAG, "Motion Detected:" + deltaX + ":" + deltaY + ":" + deltaZ);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void sendLocationInfo(Location location) {
        if(mClient != null && mIsMQTTConnected) {
            try {
                String locationUpdate = String.format("{'lat':%.7f, 'lon':%.7f}", location.getLatitude(), location.getLongitude());
                Log.d(TAG, locationUpdate);

                mClient.publish(String.format("plugs/%s/location", mDeviceId), locationUpdate.getBytes(), 0, false);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        sendLocationInfo(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
