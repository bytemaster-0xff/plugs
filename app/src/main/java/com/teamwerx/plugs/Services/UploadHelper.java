package com.teamwerx.plugs.Services;


import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.RecoverySystem;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.teamwerx.plugs.MainActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;


public class UploadHelper extends AsyncTask<Void, Integer, String> {
    private ProgressBar mProgressBar;
    private TextView mProgressPercent;
    private String mDeviceId;
    private String mServerUrl;
    String mMediaFile;
    int mServerPort;
    byte[] mMedia;
    long mTotalSize = 0;



    public UploadHelper(ProgressBar progressBar, TextView progressPercent, String deviceId,
                        String serverUrl, int serverPort){
        mProgressBar = progressBar;
        mProgressPercent = progressPercent;
        mServerUrl = serverUrl;
        mServerPort = serverPort;
        mDeviceId = deviceId;
    }

    public void setMedia(String mediaFile) {
        mMediaFile = mediaFile;
        Log.e(MainActivity.TAG, "Setting file to upload: " + mediaFile);
    }

    @Override
    protected void onPreExecute() {
        // setting progress bar to zero
        mProgressBar.setProgress(0);
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        // Making progress bar visible
        mProgressBar.setVisibility(View.VISIBLE);

        // updating progress bar value
        mProgressBar.setProgress(progress[0]);

        // updating percentage value
        mProgressPercent .setText(String.valueOf(progress[0]) + "%");
    }

    @Override
    protected String doInBackground(Void... params) {
        return uploadFile();
    }

    @SuppressWarnings("deprecation")
    private String uploadFile() {

        String responseString = null;

        try {
            HttpClient httpclient = new DefaultHttpClient();

            String uploadUri = String.format("http://%s:%s/plugs/%s/media", mServerUrl, mServerPort, mDeviceId);

            Log.e(MainActivity.TAG, "Begin upload to: " + uploadUri);
            HttpPost httpPost = new HttpPost(uploadUri);

            MultipartEntity entity = new MultipartEntity();

            File sourceFile = new File(mMediaFile);


            byte[] content = org.apache.commons.io.FileUtils.readFileToByteArray(sourceFile);

            httpPost.setEntity(new ByteArrayEntity(content));

            httpPost.addHeader("Content-Type","image/jpeg");

            // Making server call
            HttpResponse response = httpclient.execute(httpPost);
            HttpEntity r_entity = response.getEntity();

            Log.e(MainActivity.TAG, "Return from Get Entity");

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                responseString = EntityUtils.toString(r_entity);
            } else {
                responseString = "Error occurred! Http Status Code: " + statusCode;
            }

            Log.e(MainActivity.TAG, responseString);

        } catch (ClientProtocolException e) {
            responseString = e.toString();
        } catch (IOException e) {
            responseString = e.toString();
        }
        catch(Exception e) {
            responseString = "Unknown error uploading file: " + e.toString();
        }

        return responseString;
    }

    @Override
    protected void onPostExecute(String result) {
        Log.e(MainActivity.TAG, "Response from server: " + result);

        super.onPostExecute(result);
    }
}
