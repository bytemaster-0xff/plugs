package com.teamwerx.plugs;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import com.softwarelogistics.nuviot.models.Device;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MainActivity extends AppCompatActivity implements  IMqttActionListener, MqttCallback, SensorEventListener {

    MqttAndroidClient mClient;

    private float lastX, lastY, lastZ;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private float mVibrateThreshold = 0;
    public Vibrator mVibrator;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
            // fai! we dont have an accelerometer!
        }

        mVibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        Log.d(TAG, "App Startup");
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
        if (id == R.id.action_settings) {
            Log.d(TAG, "Option is selected");
            connectToMQTT();

            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    final String TAG = "PLUGSAPP";

    private void connectToMQTT(){
        Log.d(TAG, "Connecting to MQTT");

        String clientId = MqttClient.generateClientId();
         mClient = new MqttAndroidClient(this.getApplicationContext(),
                 "tcp://mqttdev.nuviot.com:1883",
                        clientId);
         mClient.setCallback(this);

        try {
            MqttConnectOptions options = new MqttConnectOptions();

            options.setUserName("kevinw");
            options.setPassword("Test1234".toCharArray());

            IMqttToken token = mClient.connect(options);
            token.setActionCallback(this);

        } catch (MqttException e) {
            Log.d(TAG, "MQTT EXCPETION");
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onSuccess(IMqttToken asyncActionToken) {
        Log.d(TAG, "onSuccess");
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
    }

    @Override
    public void messageArrived(String topic, MqttMessage message){
        Log.d(TAG, "Message arrived:" + topic + ":" + message.toString());
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

        Log.d(TAG, "Message arrived:" + deltaX + ":" + deltaY + ":" + deltaZ);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
