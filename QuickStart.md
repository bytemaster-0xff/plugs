# Quick Start


### Plugs Android Application

Please review this [video](https://www.youtube.com/watch?v=R0mtmc-4-DA) that demonstrates the capabilities of the Android application. 

1. Download the [application](https://github.com/bytemaster-0xff/plugs/raw/master/releases/app-release.apk) be sure to allow for installlation of apps [from unknown sources](https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources)
1. After the installation has completed enter the following settings:
* Server: plugshudson.sofwerx.iothost.net
* Device ID: dev001
* Target Device Id: dev002,dev003
1. Install the application on another couple of phones, use the same server, but this time use the Device Id: dev002 for one and dev003 for the other.  You can leave Target Device Id blank.
1. You should likely change the timeout on the display so it does't sleep from the default to something like more than 5 minutes if possible.
1. On the first device open the context menu and start the camera
1. On each of the devices, open the context menu and press Connect MQTT, make sure all devices have Server Connection as green.
1. One the first device, either use the camera, audio or vibration, when you do so, on the other devices you should see a toast message "Taking and uploading photo"

### Base Station

Please review this [video](https://www.youtube.com/watch?v=vRtTBfIpvDA) that demonstrates all the features of the base station web site software

1. The base station in our case is a web site.  The web address is https://www.nuviot.com, the user name and password were provided in the submission.
1. On the initial view that comes up click "Apps"
1. Then click "PLUGS Data Monitor"
1. In the left menu click on "Results and then "Results 01" click on any of the devices there to see the images that were uploaded.
1. The video that was provided also shows how you can provision new devices and device groups.
