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
import java.util.Calendar;
import java.util.Date;

public class SoundMeter {
    static final private double EMA_FILTER = 0.6;

    private String mCurrentFileName;
    private String mDeviceId;
    private String mServerName;
    private int mPort;

    private MediaRecorder mMediaDetector = null;
    private MediaRecorder mMediaRecorder = null;
    private double mEMA = 0.0;

    private String mServer;

    public SoundMeter() {

    }


    private String getFileName() {
        String deviceId = mDeviceId == null || mDeviceId.length() == 0 ? "????" : mDeviceId;
        Calendar current = Calendar.getInstance();

        return String.format("%s_%04d%02d%02d%02d%02d%02d",
                deviceId,
                current.get(Calendar.YEAR),
                current.get(Calendar.MONTH),
                current.get(Calendar.DAY_OF_MONTH),
                current.get(Calendar.HOUR_OF_DAY),
                current.get(Calendar.MINUTE),
                current.get(Calendar.SECOND));
    }

    public void start() {
        if (mMediaDetector == null) {
            mMediaDetector = new MediaRecorder();
            mMediaDetector.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaDetector.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaDetector.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaDetector.setOutputFile("/dev/null");

            mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                      //  mMediaRecorder.setNextOutputFile("/dev/null");
                    }
                }
            });

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

    public void startRecording() {
        Date date = new Date();

        mMediaDetector.stop();
        mMediaDetector = null;

        File file = new File(Environment.getExternalStorageDirectory() + "/Plugs");
        if(!file.isDirectory())
        {
            file.mkdir();
        }

        String fileName = getFileName();

        mCurrentFileName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Plugs/" + fileName + ".3gp";

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mMediaRecorder.setOutputFile(mCurrentFileName);

        Log.d("PLUGSAPP", mCurrentFileName);

        try {
            mMediaRecorder.prepare();
        }
        catch (IllegalStateException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        mMediaRecorder.start();
    }

    public void stopRecording() {
        mMediaRecorder.stop();
        mMediaRecorder = null;

        if(mDeviceId != null && mDeviceId.length() > 0 && mServerName != null && mServerName.length() > 0) {
            UploadHelper uploader = new UploadHelper(mDeviceId, this.mServerName, this.mPort);
            uploader.setMedia(mCurrentFileName, "audio/3gpp");
            uploader.execute();
        }

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