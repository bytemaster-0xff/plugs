# External Sensors
Although we are using vibration, audio and video detection from the phone there may be some cases where an external sensor will improve effectiveness.

This page will discuss that external sensor, how it can relatively easily be built and how it can be extended.

# Overview
We chose the option of simplicity for creating our external sensors, the core consists of an Arduino Pro Mini, and an HC-05 bluetooth module as well as
a power source which can simply be a cell phone charging battery pack.  A complete cost for building a sensor pack should be less than $30 which entirely consists of standard parts you can obtain from Amazon.
and only requires connecting a few wires.  For our demo we chose to use a simple PIR detection module, however based on needs this could 
easily be extended.  Once you have built your sensor module you simply need to download the Arduino Sketch and pair it with your phone.  
To build the initial version took us less than 1 hour.  If you have a little experience with Arduino's you should be able to create additional
modules in less than 30 minutes.

# Shopping Lists
1. Arduino Pro Mini (3.3V)
1. USB Cable
1. HC-05 Bluetooth Module
1. PIR Sensor
1. Jumper Wires
1. Cell Phone Battery Pack
1. Case

You will also need something to program the arduino
1. Arduino FTDI Programmer 

# Assembly Instructions
1. Create power cable
1. Solder Pins to Arduino
1. Upload software to Arduino
1. Connect Arduino to HC-05
1. Connect PIR to power cable and Arduino
1. Apply power
1. Pair sensor with HC-05
