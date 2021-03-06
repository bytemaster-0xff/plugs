# PLUGS

## PHONE-BASED LOW-COST UNATTENDED GROUND SENSOR

Software Logistics is proud to present its entry in TEAMWERX Challange to develop a tip-and-queue system based on Android.

The primary goal of this application is to deploy a system where a set of low-cost (potentially disposable) android phones can be used for some sort of presences detection, and then send a message to a different phone within this group to take a photo/video/recording.

The android application was created as a custom app, the back-end and management was built on top of our companies product, NuvIoT, a web-based platform for building IoT applications.  The backend or run-time for this system currently runs in the cloud but we are working on version that will run locally and even potentially on an android phone.  We are also exploring options to open source our run time. 

The primary focus area for phase 1 for our effort was to build a fairly complete functioning system that would demonstrate capabilities.

## Videos
1. [Video of Android PLUGS app](https://www.youtube.com/watch?v=R0mtmc-4-DA)
1. [Video of Base station](https://www.youtube.com/watch?v=vRtTBfIpvDA)

## Android APK Releases
*Please Read* [Allow Install from Unknown Sources](https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources)

[Version 1.0](https://github.com/bytemaster-0xff/plugs/raw/master/releases/app-release.apk)

## Phase I/Phase II Architecture

![alt text](./images/Architecture.png)

## Additional Resources
1. [Sensor App](SensorApp.md)
1. [External Sensors](ExternalSensors.md)
1. [Base Station](BaseStation.md)
1. [Quick Start](QuickStart.md)


## Android Application Features
1. Written as an android application with Android Studio
1. Minimum android versions 4.1
1. Works with Cellular or WiFi, however, internet connectivity is required
1. Connect to other phones and base station with MQTT
1. Location detection and reporting with GPS
1. Vibration detection with onboard accelerometer
1. Motion detection with use of onboard camera
1. Audio detection with use of onboard microphone
1. Bluetooth Connection to an external sensor, external sensor consists of Arduino and a PIR

## Base Station Features
1. Add new Android Devices
1. Ability to add a device location
1. Ability to review all triggers for sensors
1. Ability to review all media captured by phones
1. Sends notifications when motion is detected or photos are captured

## Possible next steps
1. Create a local version of our cloud-based run-time to eliminate cloud requirement
1. Capture audio/video rather than still photographs
1. Investigation of deployment/concealment options, battery life
1. Configuration motion detection thresholds
1. Explore using "Android Things" as a platform
1. Additional external sensor capabilities
1. Extend the capability of the external sensor to allow for naming Bluetooth devices
1. Integration with ATAK
1. Current version requires app to be running and in the foreground, it should be possible to build this as a service.
1. Create android Application that would act as a base station.
1. Have a "darken" screen view where even though app is running there is no unser interface.
1. Integration with Mobile [TEAMWERX Mobile Data Challenge App](https://github.com/bytemaster-0xff/oshapp) and/or GeoPackages
