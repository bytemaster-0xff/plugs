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
import java.util.Date;

public class SoundMeter {
    static final private double EMA_FILTER = 0.6;

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
    private long mTimeInStateMS = 0;
    private long mRecordingStarted = 0;

    private LocalDateTime mStartTimeInState;
    private LocalDateTime mRecordingStart;

    private String mCurrentFileName;
    private String mDeviceId;
    private String mServerName;
    private int mPort;

    private MediaRecorder mMediaDetector = null;
    private MediaRecorder mMediaRecorder = null;
    private double mEMA = 0.0;

    private String mServer;

    private DetectorSettings mDetectorSettings;

    public class DetectorSettings {
        /* Audio level to be met to start recording */
        public double ThreadholdLevel;

        /*  Maximum amount of time no sound will be recorded prior to the file being recycled.  */
        public long LoopTime;

        /* Maximum amount of time audio is recorded before it will be uploaded to the server */
        public long MaxAudioTimeMS;

        /* Number of milliseconds that have passed where threshold is not met. */
        public long SilencePeriodMS;

    }

    public SoundMeter() {
        File file = new File(Environment.getExternalStorageDirectory() + "/Plugs");
        if(!file.isDirectory())
        {
            file.mkdir();
        }

        this.mDetectorSettings = new DetectorSettings();
        this.mDetectorSettings.LoopTime = 5000;
        this.mDetectorSettings.MaxAudioTimeMS = 15000;
        this.mDetectorSettings.ThreadholdLevel = 1.0;
        this.mDetectorSettings.SilencePeriodMS = 5000;
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
        if (mMediaDetector == null) {
            mMediaDetector = new MediaRecorder();
            mMediaDetector.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaDetector.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaDetector.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaDetector.setOutputFile("/dev/null");

            /*mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                      //  mMediaRecorder.setNextOutputFile("/dev/null");
                    }
                }
            });*/

            try {
                mMediaDetector.prepare();
            }
            catch (IllegalStateException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            mMediaDetector.start();
            mEMA = 0.0;
        }
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

    public void update() {
        long millis = ChronoUnit.MILLIS.between(mRecordingStart, LocalDateTime.now());

        switch(mDetectorState) {
            case Detecting:
                Calendar current = Calendar.getInstance();

                if(mMediaRecorder.getMaxAmplitude() > this.mDetectorSettings.ThreadholdLevel) {
                    this.mDetectorState = DetectorState.Recording;
                }
                else if(millis > this.mDetectorSettings.LoopTime) {
                    mMediaRecorder.stop();
                    mCurrentFileName = getFullTimeStampFileName();
                    mMediaRecorder = startMediaRecorder((mCurrentFileName));
                }

                break;
            case Recording:
                if(millis > this.mDetectorSettings.MaxAudioTimeMS) {
                    mMediaRecorder.stop();
                    String oldFileName = mCurrentFileName;
                    mCurrentFileName = getFullTimeStampFileName();
                    mMediaRecorder = startMediaRecorder((mCurrentFileName));
                    uploadFile(oldFileName);
                }

                break;
            case PendingCompletion:
                break;
                default:
                    break;
        }
    }

    public void startRecording() {
        mMediaDetector.stop();
        mMediaDetector = null;

        mCurrentFileName = getFullTimeStampFileName();

        mMediaRecorder = startMediaRecorder(mCurrentFileName);

        mMediaRecorder.start();
    }

    private void uploadFile(String fileName) {
        if(mDeviceId != null && mDeviceId.length() > 0 && mServerName != null && mServerName.length() > 0) {
            UploadHelper uploader = new UploadHelper(mDeviceId, this.mServerName, this.mPort);
            uploader.setMedia(fileName, "audio/3gpp");
            uploader.execute();
        }
    }

    public void stopRecording() {
        mMediaRecorder.stop();
        mMediaRecorder = null;


        mCurrentFileName = null;

        mMediaDetector = new MediaRecorder();
        mMediaDetector.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaDetector.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaDetector.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mMediaDetector.setOutputFile("/dev/null");
        try {
            mMediaDetector.prepare();
        }
        catch (IllegalStateException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        mMediaDetector.start();
    }

    public void stop() {
        if (mMediaDetector != null) {
            mMediaDetector.stop();
            mMediaDetector.release();
            mMediaDetector = null;
        }
    }

    public double getAmplitude() {
        if(mMediaRecorder != null)
            return  (mMediaRecorder.getMaxAmplitude()/2700.0);

        if (mMediaDetector != null)
            return  (mMediaDetector.getMaxAmplitude()/2700.0);
        else
            return 0;
    }

    public double getAmplitudeEMA() {
        double amp = getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return mEMA;
    }
}