package com.teamwerx.plugs.Services;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.util.Collections;

public class CameraHelper {
    private CameraDevice mCamera;
    private TextureView mTextureView;
    private Size mCameraSize;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;

    private CameraCharacteristics mCameraCharacteristics;

    private Activity mActivity = null;

    private CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCamera = null;
        }
    };

    CameraCaptureSession.StateCallback mCameraCaptureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mPreviewSession = session;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Toast.makeText(mActivity, "onConfigureFailed", Toast.LENGTH_LONG).show();
        }
    };

    public CameraHelper(Activity activity, TextureView textureView) {
        mTextureView = textureView;
        mActivity = activity;
    }

    public void open() {
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    mCameraSize = map.getOutputSizes(SurfaceTexture.class)[0];

                    HandlerThread thread = new HandlerThread("OpenCamera");
                    thread.start();
                    Handler backgroundHandler = new Handler(thread.getLooper());

                    if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    manager.openCamera(cameraId, mCameraDeviceCallback, null);

                    // カメラの物理的な情報を得る
                    mCameraCharacteristics = manager.getCameraCharacteristics( cameraId );
                    return;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCaptureSession() {
        if (!mTextureView.isAvailable()) {      // textureViewが利用できない場合
            return;
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        texture.setDefaultBufferSize(mCameraSize.getWidth(), mCameraSize.getHeight());
        Surface surface = new Surface( texture );
        try {
            mPreviewBuilder = mCamera.createCaptureRequest( CameraDevice.TEMPLATE_PREVIEW );
        } catch ( CameraAccessException e ) {
            e.printStackTrace();
        }

        mPreviewBuilder.addTarget( surface );
        try {
            mCamera.createCaptureSession( Collections.singletonList( surface ), mCameraCaptureSessionCallback, null );
        } catch ( CameraAccessException e ) {
            e.printStackTrace();
        }

    }

    private void updatePreview() {
        mPreviewBuilder.set( CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE );
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            mPreviewSession.setRepeatingRequest( mPreviewBuilder.build(), null, backgroundHandler );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            mCamera.close();
            mCamera = null;
        } catch ( Exception e ) {
            Log.e( "@CameraClose", e.toString());
        }
    }

    public float[] getAngle() {
        // 物理センサのサイズを取得（単位はミリメートル）
        // SizeFクラス　float型の幅widthと高さheightの情報を持つ
        SizeF physicalSize = mCameraCharacteristics.get( mCameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE );
        Log.d( "CameraCharacteristics", "物理サイズ : " + physicalSize.getWidth() + ", " + physicalSize.getHeight() );

        // 焦点距離取得
        float[] focalLength = mCameraCharacteristics.get( mCameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS );

        // 画素配列の画素数取得
        // Sizeクラス　int型の幅widthと高さheightの情報を持つ
        Size fullArraySize = mCameraCharacteristics.get( mCameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE );
        Log.d( "CameraCharacteristics", "画素配列幅 : " + fullArraySize.getWidth() + ", " + fullArraySize.getHeight() );

        // 有効な配列領域取得( = 切り取り領域[ 0, 0, activeRect.width, activeRect.height ])
        Rect activeRect = mCameraCharacteristics.get( mCameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE );
        Log.d( "CameraCharacteristics", "有効配列幅 : " + activeRect.width() + ", " + activeRect.height() );

        // 出力ストリームのサイズ取得
        Size outputStreamSize = new Size( mTextureView.getWidth(), mTextureView.getHeight() );
        Log.d( "CameraCharacteristics", "出力ストリーム : " + outputStreamSize.getWidth() + ", " + outputStreamSize.getHeight() );

        float[] angle = new float[2];
        angle[0] = 2f * (float)Math.toDegrees( Math.atan( physicalSize.getWidth()  / ( 2 * focalLength[0] ) ) ); // 横
        angle[1] = 2f * (float)Math.toDegrees( Math.atan( physicalSize.getHeight() / ( 2 * focalLength[0] ) ) ); // 縦
        Log.d("getAngle",  angle[0] + ", " + angle[1] + ", " );
        return angle;
    }

}
