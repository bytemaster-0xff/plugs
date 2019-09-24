package com.teamwerx.plugs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.teamwerx.plugs.BlueTooth.BluetoothDeviceAdapter;
import com.teamwerx.plugs.BlueTooth.BluetoothService;
import com.teamwerx.plugs.BlueTooth.Constants;
import com.teamwerx.plugs.Services.CameraHelper;
import com.teamwerx.plugs.Services.UploadHelper;
import com.teamwerx.plugs.motion_detection.IMotionDetectedListener;
import com.teamwerx.plugs.noise.SoundMeter;

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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity
        implements IMqttActionListener, MqttCallback, IMotionDetectedListener,
        SensorEventListener, LocationListener {

    MqttAndroidClient mClient;

    boolean mHasStoragePermissions = false;
    boolean mHasGPSPermissions = false;
    boolean mHasCemeraPermissions = false;
    boolean mHasRecorderPermissions = false;

    private CameraHelper mCamera;

    // battery sensor begin
    // https://developer.android.com/training/monitoring-device-state/battery-monitoring.html#java
    private Timer batteryTimer = new Timer();
    private TimerTask batteryTask = new TimerTask(){
        @Override
        public void run(){
            if (mIsMQTTConnected) {
                try {
                    IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = getApplicationContext().registerReceiver(null, batteryFilter);

                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 0);
                    float voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                    float percent = level / (float)scale  * 100;
                    voltage *= 0.001;

                    String json = String.format("{\"level\":%d,\"scale\":%d,\"percent\":%f,\"voltage\":%f}", level, scale, percent, voltage);
                    Log.d(TAG, json);

                    mClient.publish(String.format("plugs/%s/batt", mDeviceId), json.getBytes(), 0, false);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    // battery sensor end

    private float lastX, lastY, lastZ;

    private SurfaceView mCameraPreview;
    private View mMQTTConnectionStatus;
    private View mLocationStatus;
    private View mVibrationDetected;
    private View mVideoMotionDected;
    private View mExternalSensorStatus;
    private View mExternalSensorMotionStatus;
    private View mAudioSensorStatus;

    Calendar mMotionDetectedDateStamp = null;
    private TextView mLocationInfo;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private SoundMeter mSoundMeter;

    private boolean mSoundDetected;
    private Calendar mSoundDetectedDateStamp;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDeviceAdapter bluetoothDevicesAdapter;

    public final static String TAG = "PLUGSAPP";

    String mServerHostName;

    final int SERVER_HOST_PORT = 8050;

    private Handler resetVibrationHandler = new Handler();
    private Handler mWipeHandler = new Handler();
    private Handler mExternalSensorHeartbeatTimeout = new Handler();
    private Handler mNoiseDetectionHandler = new Handler();

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1; // 1 minute

    private static final long MOTION_INTERVAL = 7;

    private Location mLastLocation;
    private LocationManager mLocationManager;
    private String mDeviceId;

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
        String targetDeviceIds = prefs.getString(TARGET_DEVICES, "");
        parseDeviceIDs(targetDeviceIds);

        mCameraPreview = findViewById(R.id.cameraPreview);
        mVideoMotionDected = findViewById(R.id.videoMotionStatusDisplay);
        mVibrationDetected = findViewById(R.id.vibrationStatusDisplay);
        mMQTTConnectionStatus = findViewById(R.id.mqttConnectionStatus);
        mLocationStatus = findViewById(R.id.locationStatusDisplay);
        mLocationStatus = findViewById(R.id.locationStatusDisplay);
        mLocationInfo = findViewById(R.id.locationStatus);
        mAudioSensorStatus = findViewById(R.id.audioSensorStatus);

        mExternalSensorStatus = findViewById(R.id.externalSensorStatus);
        mExternalSensorMotionStatus = findViewById(R.id.externalSensorMotionStatus);
        mExternalSensorHeartbeatTimeout.postDelayed(externalSensorHeartbeatTimeout,4000);
        mNoiseDetectionHandler.postDelayed(detectNoise, 250);

        verifyAppPermissions();
        initAccelerometer();

        batteryTimer.schedule(batteryTask, 1000 * 5, 1000 * 5);

        if(mHasRecorderPermissions) {
            initMicophone();
        }

        if (mHasGPSPermissions) {
            initGPS();
        }
    }

    private static final int REQUEST_PERMISSIONS = 1;
    private static String[] APP_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    private void initMicophone() {
        mSoundMeter = new SoundMeter();
        mSoundMeter.start();

        mSoundMeter.configureServer(mServerHostName, mDeviceId, SERVER_HOST_PORT);
    }

    @SuppressLint("MissingPermission")
    private void initGPS() {
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES,
                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
        mLastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        setCurrentLocation(mLastLocation);
    }

    private void initAccelerometer() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.d(TAG, "Sorry, no accelerator");
        }
    }

    public void verifyAppPermissions() {
        // Check if we have write permission
        mHasStoragePermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        mHasGPSPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        mHasCemeraPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        mHasRecorderPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        if (!mHasStoragePermissions || !mHasGPSPermissions || !mHasCemeraPermissions || !mHasRecorderPermissions) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    APP_PERMISSIONS,
                    REQUEST_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS: {
                for(int idx = 0; idx < permissions.length; ++idx) {
                    //YUCK THERE HAS TO BE A BETTER WAY!
                    if(permissions[idx].contentEquals(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                            grantResults[idx] == PackageManager.PERMISSION_GRANTED) {
                        mHasStoragePermissions = true;
                    }

                    if(permissions[idx].contentEquals(Manifest.permission.ACCESS_FINE_LOCATION) &&
                            grantResults[idx] == PackageManager.PERMISSION_GRANTED) {
                        mHasGPSPermissions = true;
                    }

                    if(permissions[idx].contentEquals(Manifest.permission.CAMERA) &&
                            grantResults[idx] == PackageManager.PERMISSION_GRANTED) {
                        mHasCemeraPermissions = true;
                    }

                    if(permissions[idx].contentEquals(Manifest.permission.RECORD_AUDIO) &&
                            grantResults[idx] == PackageManager.PERMISSION_GRANTED) {
                        mHasRecorderPermissions = true;
                    }
                }

                if(mHasRecorderPermissions && mSoundMeter == null) {
                    initMicophone();
                }

                if(mHasGPSPermissions && mLocationManager == null) {
                    initGPS();
                }
            }
        }
    }

    private boolean mIsMQTTConnected = false;
    private boolean mIsCameraActive = false;

    private List<String> mTargetDevices = new ArrayList<>();

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
        int id = item.getItemId();

        switch(id)
        {
            case R.id.action_camera_start: startCamera(); break;
            case R.id.action_camera_stop: stopCamera(); break;
            case R.id.action_mqtt_connect: connectToMQTT(); break;
            case R.id.action_mqtt_disconnect: disconnectFromMQTT(); break;
            case R.id.action_take_photo: takePhoto(); break;
            case R.id.action_send_location: sendCurrentLocation(); break;
            case R.id.action_settings: showSettingsDialog(); break;
            case R.id.action_connect_to_sensor: startSearching();
            default: super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void disconnectFromMQTT() {
        if(mClient != null){
            mIsMQTTConnected = false;
            mClient.close();
            mClient = null;
            mMQTTConnectionStatus.setBackgroundColor(Color.GREEN);
            invalidateOptionsMenu();
        }
    }

    private void connectToMQTT(){
        Log.d(TAG, "Connecting to MQTT");

        String clientId = MqttClient.generateClientId();
        mClient = new MqttAndroidClient(this.getApplicationContext(), "tcp://" + mServerHostName + ":1883", clientId);
        mClient.setCallback(this);

        try {
            MqttConnectOptions options = new MqttConnectOptions();

            options.setUserName("kevinw");
            options.setPassword("Test1234".toCharArray());

            IMqttToken token = mClient.connect(options);
            //IMqttToken token = mClient.connect();
            token.setActionCallback(this);

        } catch (MqttException e) {
            Log.d(TAG, "MQTT EXCPETION");
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    private void startCamera() {
        if(mHasCemeraPermissions) {
            if (mCamera == null) {
                mCamera = new CameraHelper(this, mCameraPreview);
                mCamera.open();
                mCameraPreview.setVisibility(View.VISIBLE);
                mIsCameraActive = true;
                invalidateOptionsMenu();
            }
        }
        else {
            verifyAppPermissions();
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
            mLocationStatus.setBackgroundColor(Color.RED);
            mLocationInfo.setText("Location unavailable");
            Toast.makeText(this, "Sorry location information is not available", Toast.LENGTH_SHORT);
        }
    }

    final String SERVER_KEY = "SERVER_SETTING";
    final String TARGET_DEVICES = "TARGET_DEVICES_SETTINGS";
    final String DEVICE_ID_KEY = "DEVICE_ID_SETTING";

    private boolean parseDeviceIDs(String idString) {
        String[] ids = idString.split(",");

        mTargetDevices.clear();
        for(String part : ids){
            if(part != null && part.length()> 0) {
                mTargetDevices.add(part);
            }
        }

        return true;
    }

    private void showSettingsDialog() {
        LayoutInflater inflater = this.getLayoutInflater();

        View content = inflater.inflate(R.layout.settings_dlg_view, null);

        final SharedPreferences prefs = this.getPreferences(Context.MODE_PRIVATE);

        final EditText srvr = content.findViewById(R.id.settings_server);
        final EditText deviceId = content.findViewById(R.id.settings_device_id);
        final EditText targetDeviceIds = content.findViewById(R.id.settings_target_device_id);

        srvr.setText(mServerHostName);
        deviceId.setText(mDeviceId);;


        String targetDeviceString = "";
        for(String targetDevice : mTargetDevices) {
            targetDeviceString += String.format("%s%s", targetDeviceString.length() > 0 ? "," : "", targetDevice);
        }

        targetDeviceIds.setText(targetDeviceString);

        AlertDialog.Builder dlgBldr = new AlertDialog.Builder(this);
        dlgBldr.setTitle(R.string.app_name);
        dlgBldr.setView(content);

        dlgBldr.setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dlg, int which){
                mServerHostName = srvr.getText().toString();
                mDeviceId = deviceId.getText().toString();
                String idString = targetDeviceIds.getText().toString();

                prefs.edit().putString(SERVER_KEY, mServerHostName).commit();
                prefs.edit().putString(DEVICE_ID_KEY, mDeviceId).commit();

                if(parseDeviceIDs(idString)) {
                    prefs.edit().putString(TARGET_DEVICES, idString).commit();
                }

                mSoundMeter.configureServer(mServerHostName, mDeviceId, SERVER_HOST_PORT);

                invalidateOptionsMenu();
            }
        });

        dlgBldr.show();
    }

    @SuppressLint("NewApi")
    private void takePhoto() {
        if(mCameraPreview.getVisibility() == View.VISIBLE &&
                mCamera != null && mIsCameraActive) {

            Toast.makeText(MainActivity.this, "Taking and uploading photo", Toast.LENGTH_LONG).show();

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

            UploadHelper uploader = new UploadHelper(mDeviceId, mServerHostName, SERVER_HOST_PORT);
            uploader.setMedia(fullFileName, "image/jpeg");
            uploader.execute();
        }
        else {
            Toast.makeText(MainActivity.this, "Camera not ready.", Toast.LENGTH_LONG).show();
        }
    }

    Calendar lastHeartbeat = null;

    private Runnable externalSensorHeartbeatTimeout = new Runnable() {
        @Override
        public void run() {
            if(lastHeartbeat != null) {
                long deltaT = (Calendar.getInstance().getTime().getTime() - lastHeartbeat.getTime().getTime());
                if (deltaT > 3000){
                    mExternalSensorStatus.setBackgroundColor(Color.LTGRAY);
                }
            }

            mExternalSensorHeartbeatTimeout.postDelayed(externalSensorHeartbeatTimeout,4000);
        }
    };

    public void externalHeartbeatPing() {
        lastHeartbeat = Calendar.getInstance();
        mExternalSensorStatus.setBackgroundColor(Color.GREEN);
    }

    public void externalMotion(boolean hasMotion) {
        if(hasMotion) {
            mExternalSensorMotionStatus.setBackgroundColor(Color.GREEN);

            if (mIsMQTTConnected) {
                try {
                    mClient.publish(String.format("plugs/%s/pir", mDeviceId), "{motion:true}".getBytes(), 0, false);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }

            sendCaptureMedia();
        }
        else {
            mExternalSensorMotionStatus.setBackgroundColor(Color.LTGRAY);
        }
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "onSuccess");
        try {
            mClient.subscribe(String.format("incoming/%s/+", mDeviceId),0);
            invalidateOptionsMenu();
            mIsMQTTConnected = true;

            runOnUiThread(new Runnable() {
                @Override
                public void run() { mMQTTConnectionStatus.setBackgroundColor(Color.GREEN); }
            });

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
    public void onFailure(IMqttToken asyncActionToken, final Throwable exception) {
        // Something went wrong e.g. connection timeout or firewall problems
        Log.d(TAG, "onFailure");
        Throwable cause = exception.getCause();
<<<<<<< HEAD

        if(cause != null) {
            Log.d(TAG, exception.getCause().getMessage());
        }

=======
        if(cause != null)
            Log.d(TAG, cause.getMessage());
>>>>>>> 486cff7b5984492b66d2173e3ac32a7204a343b9
        Log.d(TAG, "onFailure");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            mMQTTConnectionStatus.setBackgroundColor(Color.RED);
            Toast.makeText(MainActivity.this, "Could not connect to MQTT Server - " + exception.getCause().getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        invalidateOptionsMenu();
    }

    @Override
    public void connectionLost(Throwable cause){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMQTTConnectionStatus.setBackgroundColor(Color.RED);
            }});

        Toast.makeText(MainActivity.this, "Lost connection to MQTT Server", Toast.LENGTH_LONG).show();

        if(cause != null) {
            Log.d(TAG, "MQTT Server connection lost" + cause.getMessage());
        }
        else {
            Log.d(TAG, "MQTT Server connection lost");
        }

        mIsMQTTConnected = false;
        invalidateOptionsMenu();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message){
        Log.d(TAG, "Message arrived:" + topic + ":" + message.toString());

        if(topic.contains("startcamera")){
            startCamera();
        }
        if(topic.contains("wipe")){
            wipeDevice();
        }
        else if(topic.contains("stopcamera")) {
            stopCamera();
        }
        else if(topic.contains("takephoto")) {
            takePhoto();
        }
    }

    private void wipeDevice() {
        final String dir = Environment.getExternalStorageDirectory() + "/AppPicFolder/";
        Log.d(TAG, "Removing local picture directory: " + dir);

        File newdir = new File(dir);
        newdir.delete();

        SharedPreferences prefs = this.getPreferences(Context.MODE_PRIVATE);
        prefs.edit().putString(SERVER_KEY, "").commit();
        prefs.edit().putString(DEVICE_ID_KEY, "").commit();
        prefs.edit().putString(TARGET_DEVICES, "").commit();

        mWipeHandler.postDelayed(wipeExit, 2000);

        Toast.makeText(this, "Wiping device", Toast.LENGTH_LONG).show();
    }

    private Runnable wipeExit = new Runnable() {
        @Override
        public void run() {
            MainActivity.this.finish();
        }
    };

    @Override
    public void deliveryComplete(IMqttDeliveryToken token){
        Log.d(TAG, "Delivery complete");
    }

    private Runnable resetVibration = new Runnable() {
        @Override
        public void run() {
        mVibrationDetected.setBackgroundColor(Color.LTGRAY);
        }
    };

    private Runnable detectNoise = new Runnable() {
        @Override
        public void run() {
            if(mSoundMeter != null) {
                if(mSoundMeter.getAmplitude() > 1.0) {
                    if(!mSoundDetected) {
                        if (mIsMQTTConnected) {
                             try {
                                 mClient.publish(String.format("plugs/%s/audio", mDeviceId), "{noise:true}".getBytes(), 0, false);
                             } catch (MqttException e) {
                                 e.printStackTrace();
                             }
                         }
                         mSoundDetected = true;

                        sendCaptureMedia();
                        mSoundMeter.startRecording();
                    }

                    mAudioSensorStatus.setBackgroundColor(Color.GREEN);

                    mSoundDetectedDateStamp = Calendar.getInstance();
                }
                else if(mSoundDetected && mSoundDetectedDateStamp != null){
                    if ((Calendar.getInstance().getTime().getTime() - mSoundDetectedDateStamp.getTime().getTime()) > 3000) {
                        mSoundDetected = false;
                        mSoundDetectedDateStamp = null;
                        mAudioSensorStatus.setBackgroundColor(Color.LTGRAY);
                        mSoundMeter.stopRecording();
                    }
                }
            }

            mNoiseDetectionHandler.postDelayed(detectNoise, 10);
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        float deltaX = Math.abs(lastX - event.values[0]);
        float deltaY = Math.abs(lastY - event.values[1]);
        float deltaZ = Math.abs(lastZ - event.values[2]);

        lastX = event.values[0];
        lastY = event.values[1];
        lastZ = event.values[2];

        if (deltaX > 0.1 || deltaY > 0.1 || deltaZ > 0.1) {
            if (mMotionDetectedDateStamp != null) {
                if ((Calendar.getInstance().getTime().getTime() - mMotionDetectedDateStamp.getTime().getTime()) / 1000 < MOTION_INTERVAL) {
                    return;
                }
            }

            Log.d(TAG, "Motion Detected:" + deltaX + ":" + deltaY + ":" + deltaZ);

            mMotionDetectedDateStamp = Calendar.getInstance();
            if (mIsMQTTConnected) {
                try {
                    mClient.publish(String.format("plugs/%s/vibration", mDeviceId), "{vibration:true}".getBytes(), 0, false);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }

            sendCaptureMedia();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mVibrationDetected.setBackgroundColor(Color.GREEN);
                    resetVibrationHandler.postDelayed(resetVibration, 5000);
                }
            });
        }
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

    public void setCurrentLocation(Location location) {
        if(location != null) {
            mLastLocation = location;
            mLocationStatus.setBackgroundColor(Color.GREEN);
            String locationUpdate = String.format("Lat: %.4f, Lon: %.4f", mLastLocation.getLatitude(), mLastLocation.getLongitude());
            mLocationInfo.setText(locationUpdate);
        }
        else {
            mLocationStatus.setBackgroundColor(Color.RED);
            mLocationInfo.setText("Location unavailable");
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        setCurrentLocation(location);
        sendLocationInfo(location);
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    Handler resetVideoMotionHandler = new Handler();
    private Runnable resetVideoMotion = new Runnable() {
        @Override
        public void run() {
            mVideoMotionDected.setBackgroundColor(Color.LTGRAY);
        }
    };

    private void sendCaptureMedia() {
        try {
            if (mIsMQTTConnected) {
                for (String targetDeviceId : mTargetDevices) {
                    String topic = String.format("incoming/%s/takephoto", targetDeviceId);
                    Log.d(TAG, topic);
                    mClient.publish(topic, "{capture:true}".getBytes(), 0, false);
                }
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMotionDetected() {
        try {
            if(mIsMQTTConnected) {
                mClient.publish(String.format("plugs/%s/videomotion", mDeviceId), "{videoMotion:true}".getBytes(), 0, false);
            }

        } catch (MqttException e) {
            e.printStackTrace();
        }

        sendCaptureMedia();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mVideoMotionDected.setBackgroundColor(Color.GREEN);
            }});

        resetVideoMotionHandler.postDelayed(resetVideoMotion, 3000);
    }

    private void enableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
    }

    BluetoothService mBluetoothService;

    @Override protected void onStart() {
        super.onStart();

        Log.d(MainActivity.TAG, "Registering receiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
    }

    @Override protected void onStop() {
        super.onStop();
        Log.d(MainActivity.TAG, "Receiver unregistered");
        unregisterReceiver(mReceiver);
    }

    private void startSearching(){
        bluetoothDevicesAdapter = new BluetoothDeviceAdapter(this);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter.isEnabled()) {
            if(bluetoothAdapter == null) {
                Toast.makeText(this, "No bluetooth adapter found", Toast.LENGTH_SHORT).show();
            }
            else {
                if(!bluetoothAdapter.startDiscovery()) {
                    Toast.makeText(this, "Failed to start searching", Toast.LENGTH_SHORT).show();
                }
            }
        }
        else {
            enableBluetooth();
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                device.fetchUuidsWithSdp();


                if(device.getName().contentEquals("HC-05")) {
                    bluetoothAdapter.cancelDiscovery();

                    mBluetoothMessageHandler = new BluetoothMessageHandler(MainActivity.this);
                    mBluetoothService = new BluetoothService(mBluetoothMessageHandler, device);
                    mBluetoothService.connect();

                    Log.d(MainActivity.TAG, String.format("Found device %s", device.getName()));
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(MainActivity.TAG, "Finished searching");
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Toast.makeText(MainActivity.this, "Bluetooth turned off", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
    };

    BluetoothMessageHandler mBluetoothMessageHandler;

    private class BluetoothMessageHandler extends Handler {

        private final WeakReference<MainActivity> mActivity;

        public BluetoothMessageHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case Constants.STATE_CONNECTED:
                            Toast.makeText(MainActivity.this, "Bluetooth connected", Toast.LENGTH_SHORT).show();
                            break;
                        case Constants.STATE_CONNECTING:
                            Toast.makeText(MainActivity.this, "Bluetooth connecting", Toast.LENGTH_SHORT).show();
                            break;
                        case Constants.STATE_NONE:
                            break;
                        case Constants.STATE_ERROR:
                            Toast.makeText(MainActivity.this, "Error Connecting", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    /* Not currently sending anything to BT */
                    break;
                case Constants.MESSAGE_READ:
                    String readMessage = (String) msg.obj;
                    if(readMessage.contains("ping")) {
                        MainActivity.this.externalHeartbeatPing();
                    }
                    else if(readMessage.contains("on")) {
                        MainActivity.this.externalMotion(true);
                    }
                    else if(readMessage.contains("off")) {
                        MainActivity.this.externalMotion(false);
                    }
                    break;

                case Constants.MESSAGE_SNACKBAR:



                    break;
            }
        }


    }
}
