/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.teamwerx.plugs.noise;

import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.teamwerx.plugs.Services.UploadHelper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;

public class SoundMeter {
    public enum DetectorState {
        /* Not detecting */
        Idle,

        /* Waiting for threshold to be exceeded. */
        Detecting,

        /* A level higher then the audio threshold has been seen, continue to record audio */
        Recording,

        /* Wait for a low level for a certain amount of time, before terminating the recording */
        PendingCompletion
    }

    /*
            v---------------------------|
        Detecting => Recording => PendingCompletion
                          ^-------------|
     */

    private DetectorState mDetectorState = DetectorState.Detecting.Idle;

    private LocalDateTime mStartTimeInState;
    private LocalDateTime mRecordingStart;

    private String mCurrentFileName;
    private String mDeviceId;
    private String mServerName;
    private int mPort;

    private MediaRecorder mMediaRecorder = null;

    private DetectorSettings mDetectorSettings;

    public class DetectorSettings {
        /* Audio level to be met to start recording */
        public double DetectionThreshold;

        /*  Maximum amount of time no sound will be recorded prior to the file being recycled.  */
        public long LoopTimeMS;

        /* Maximum amount of time audio is recorded before it will be uploaded to the server */
        public long MaxRecordingTimeMS;

        /* Number of milliseconds that have passed where threshold is not met. */
        public long BelowThresholdPeriodMS;

    }

    public SoundMeter() {
        File file = new File(Environment.getExternalStorageDirectory() + "/Plugs");
        if(!file.isDirectory())
        {
            file.mkdir();
        }

        this.mDetectorSettings = new DetectorSettings();
        this.mDetectorSettings.LoopTimeMS = 5000;
        this.mDetectorSettings.MaxRecordingTimeMS = 15000;
        this.mDetectorSettings.DetectionThreshold = 0.5;
        this.mDetectorSettings.BelowThresholdPeriodMS = 5000;
    }

    private String getFullTimeStampFileName() {
        String deviceId = mDeviceId == null || mDeviceId.length() == 0 ? "????" : mDeviceId;
        Calendar current = Calendar.getInstance();

        String fileName = String.format("%s_%04d%02d%02d%02d%02d%02d",
                deviceId,
                current.get(Calendar.YEAR),
                current.get(Calendar.MONTH),
                current.get(Calendar.DAY_OF_MONTH),
                current.get(Calendar.HOUR_OF_DAY),
                current.get(Calendar.MINUTE),
                current.get(Calendar.SECOND));

        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Plugs/" + fileName + ".3gp";
    }

    public void start() {
        mCurrentFileName = getFullTimeStampFileName();
        mMediaRecorder = startMediaRecorder((mCurrentFileName));
        mMediaRecorder.start();
        mRecordingStart = LocalDateTime.now();
        mStartTimeInState = LocalDateTime.now();
        mDetectorState = DetectorState.Detecting;
    }

    public void configureServer(String serverName, String deviceId, int port) {
        this.mDeviceId = deviceId;
        this.mServerName = serverName;
        this.mPort = port;
    }

    MediaRecorder startMediaRecorder(String fileName) {
        MediaRecorder  mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(fileName);

        try {
            mediaRecorder.prepare();
        }
        catch (IllegalStateException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return mediaRecorder;
    }

    private Boolean audioDetected() {
        double level = mMediaRecorder.getMaxAmplitude() / 2700.0;
        Log.d("STATETRANSITION", String.format("audio level %f", level) );
        return level > this.mDetectorSettings.DetectionThreshold;
    }

    private void cycleAudio(Boolean upload, Boolean delete) {
        String oldFileName = mCurrentFileName;

        mMediaRecorder.stop();
        mCurrentFileName = getFullTimeStampFileName();
        mMediaRecorder = startMediaRecorder((mCurrentFileName));
        mMediaRecorder.start();
        mRecordingStart = LocalDateTime.now();

        if(delete) {
            File file = new File(oldFileName);
            file.delete();
        }

        if(upload) {
            uploadFile(oldFileName);
        }
    }

    public Boolean update() {
        long millis = ChronoUnit.MILLIS.between(mRecordingStart, LocalDateTime.now());
        long timeInState = ChronoUnit.MILLIS.between(mStartTimeInState, LocalDateTime.now());

        switch(mDetectorState) {
            case Detecting:
                if(audioDetected()) {
                    Log.d("STATETRANSITION", "Detected Sound.");
                    this.mDetectorState = DetectorState.Recording;
                    mStartTimeInState = LocalDateTime.now();
                    return true;
                }
                else if(millis > this.mDetectorSettings.LoopTimeMS) {
                    cycleAudio(false, true);

                    Log.d("STATETRANSITION", "No sound detected, recycling file.");
                }

                return false;

            case Recording:
                if(millis > this.mDetectorSettings.MaxRecordingTimeMS) {
                    cycleAudio(true, false);

                    Log.d("STATETRANSITION", "Max recording reached uploading sample.");
                }

                if(!audioDetected()) {
                    mStartTimeInState = LocalDateTime.now();
                    mDetectorState = DetectorState.PendingCompletion;

                    Log.d("STATETRANSITION", "Detected silence.");
                    return false;
                }
                else {
                    return true;
                }

            case PendingCompletion:
                if(timeInState > this.mDetectorSettings.BelowThresholdPeriodMS) {
                    Log.d("STATETRANSITION", "Silence long enough to terminate event, detecting.");

                    cycleAudio(true, false);

                    this.mDetectorState = DetectorState.Detecting;
                    mStartTimeInState = LocalDateTime.now();

                    return false;
                }
                else  if(audioDetected()) {
                    this.mDetectorState = DetectorState.Recording;
                    mStartTimeInState = LocalDateTime.now();

                    Log.d("STATETRANSITION", "Silence broken, continuing to read.");
                    return true;
                }
                default:
                    return false;
        }
    }

    private void uploadFile(String fileName) {
        Log.d("STATETRANSITION", "Uploading => " + fileName);
        if(mDeviceId != null && mDeviceId.length() > 0 && mServerName != null && mServerName.length() > 0) {
            UploadHelper uploader = new UploadHelper(mDeviceId, this.mServerName, this.mPort);
            uploader.setMedia(fileName, "audio/3gpp");
            uploader.execute();
        }
        else {
            Log.d("STATETRANSITION", "Not Configured.");
        }
    }
}