package com.teamwerx.plugs;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import com.softwarelogistics.nuviot.models.Device;
import com.teamwerx.plugs.Services.CameraHelper;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements  IMqttActionListener, MqttCallback, SensorEventListener {

    MqttAndroidClient mClient;

    private float lastX, lastY, lastZ;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private float mVibrateThreshold = 0;
    public Vibrator mVibrator;

    TextureView mCameraPreview;

    final String TAG = "PLUGSAPP";
    final String MQTT_CLIENT = "plugshudson.sofwerx.iothost.net";

    private CameraHelper mCamera;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    int TAKE_PHOTO_CODE = 0;
    public static int count = 110;


    private String mDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDeviceId = "dev001";

        mCameraPreview = findViewById(R.id.cameraPreview);

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
            // success! we have an accelerometer

            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            mVibrateThreshold = mAccelerometer.getMaximumRange() / 2;

        } else {
            Log.d(TAG, "Sorry, no accelerator");
        }

        verifyStoragePermissions(this);

        mVibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        Log.d(TAG, "App Startup");
    }

    boolean mHasStoragePermissions = false;
    boolean mHasGPSPermissions = false;
    boolean mHasCemeraPermissions = false;


    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
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
        menu.findItem(R.id.action_mqtt_connect).setEnabled(!mIsMQTTConnected);
        menu.findItem(R.id.action_mqtt_disconnect).setEnabled(mIsMQTTConnected);
        menu.findItem(R.id.action_camera_start).setEnabled(!mIsCameraActive);
        menu.findItem(R.id.action_camera_stop).setEnabled(mIsCameraActive);
        menu.findItem(R.id.action_take_photo).setEnabled(mIsCameraActive);
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
            default: super.onOptionsItemSelected(item);
        }

        // If the default case was not executed (i.e. menu was handled, return true)
        return true;
    }

    private void disconnectFromMQTT() {
        if(mClient != null){
            mClient.close();
            mClient = null;
        }
    }

    private void connectToMQTT(){
        Log.d(TAG, "Connecting to MQTT");

        String clientId = MqttClient.generateClientId();
         mClient = new MqttAndroidClient(this.getApplicationContext(),
                 "tcp://" + MQTT_CLIENT + ":1883",
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

    private void startRecordingAudio() {


    }

    private void stopRecordingAudio() {

    }

    private void takePhoto() {
        if(mCameraPreview.getVisibility() == View.VISIBLE &&
                mCamera != null && mIsCameraActive) {
            Bitmap original = mCameraPreview.getBitmap();
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

            Log.d(TAG, "New File Name: " + fileName);

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

            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                //TODO: Upload to NuvIoT
                byte[] bitmapdata = bos.toByteArray();
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "onSuccess");
        try {
            mClient.subscribe("incoming/dev001/+",0);
            invalidateOptionsMenu();
            mIsMQTTConnected = true;
        } catch (MqttException e) {
            Log.d(TAG, "MQTT EXCPETION");
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        deltaX = Math.abs(lastX - event.values[0]);
        deltaY = Math.abs(lastY - event.values[1]);
        deltaZ = Math.abs(lastZ - event.values[2]);

        lastX = event.values[0];
        lastY = event.values[1];
        lastZ = event.values[2];

        if(mClient != null && mIsMQTTConnected) {
            if (deltaX > 0.1 || deltaY > 0.1 || deltaZ > 0.1) {
                try {
                    mClient.publish("plugs/dev001/vibration", "{vibration:true}".getBytes(), 0, false);
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
}
