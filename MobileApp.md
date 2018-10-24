# PLUGS - Android Application

Currently the Android Applications focus is on demonstrating capabilities.  In phase 2 it would be focused on deployment/background operation.

To configure the android application you need to have three settings

1. URL of the MQTT Server, for Phase 1, this will be a URL in the cloud and unless a different environment is setup the default url will be `plugshudson.sofwerx.iothost.net`
1. Id of the device.  For easiest configuration your id shold be between 5 and 20 characters, consist only of lower case letters and numbers and begin with a lower case letter.  These are formal constraints but will likely make identifying and using device ids easier
1. Target device ids, create a comma delimited list of which device you wish to capture media when this device detects something.  An example would be `dev002,dev003` if these devices are specified and the device detects something, a message will be send to `dev002` and `dev003` to capture media.

_note_: For phase one as mentioned above we will be manually turning on the servers, in a deployable version once configured it will simply be enough to start the app.
Once you have your android application configured, simply open up the menu and "connect to mqtt" if your connection is successful the XXX will turn green.
Next simply start the camera.  Finally if your device was paired with an external sensor, click on "connect to external sensor".  With all three of these options
and your device detecting it's location the screen should look like:

