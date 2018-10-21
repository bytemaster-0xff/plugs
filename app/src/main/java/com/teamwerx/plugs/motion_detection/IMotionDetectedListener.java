package com.teamwerx.plugs.motion_detection;

import org.eclipse.paho.client.mqttv3.IMqttToken;

public interface IMotionDetectedListener {
    void onMotionDetected();
}
