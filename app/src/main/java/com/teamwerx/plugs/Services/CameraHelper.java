package com.teamwerx.plugs.Services;

import android.app.Activity;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.teamwerx.plugs.MainActivity;
import com.teamwerx.plugs.data.Preferences;
import com.teamwerx.plugs.image.ImageProcessing;
import com.teamwerx.plugs.motion_detection.AggregateLumaMotionDetection;
import com.teamwerx.plugs.motion_detection.IMotionDetection;
import com.teamwerx.plugs.motion_detection.LumaMotionDetection;
import com.teamwerx.plugs.motion_detection.RgbMotionDetection;

import java.util.concurrent.atomic.AtomicBoolean;

public class CameraHelper {
    private static Camera mCamera = null;

    private SurfaceView mTextureView = null;
    private static SurfaceHolder mPreviewHolder = null;

    private Size mCameraSize;
    private boolean mPreviewActive;
    private Activity mActivity = null;

    private static long mReferenceTime = 0;
    private static volatile AtomicBoolean mProcessing = new AtomicBoolean(false);
    private static IMotionDetection mMotionDetector = null;

    private static Bitmap mCurrentImage;

    public CameraHelper(Activity activity, SurfaceView textureView) {
        mTextureView = textureView;
        mActivity = activity;

        if (Preferences.USE_RGB) {
            mMotionDetector = new RgbMotionDetection();
        } else if (Preferences.USE_LUMA) {
            mMotionDetector = new LumaMotionDetection();
        } else {
            mMotionDetector = new AggregateLumaMotionDetection();
        }
    }

    private PreviewCallback previewCallback = new PreviewCallback() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) return;
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;

            DetectionThread thread = new DetectionThread(data, size.width, size.height);
            thread.start();
        }
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                mCamera.setPreviewDisplay(mPreviewHolder);
                mCamera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e(MainActivity.TAG, "Exception in setPreviewDisplay()", t);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = mCamera.getParameters();
            Camera.Size size = getBestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(MainActivity.TAG, "Using width=" + size.width + " height=" + size.height);
            }
            mCamera.setParameters(parameters);
            mCamera.startPreview();
            mPreviewActive = true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    private static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) result = size;
                }
            }
        }

        return result;
    }

    public void open() {
        mCamera = Camera.open();

        mPreviewHolder = mTextureView.getHolder();
        mPreviewHolder.addCallback(surfaceCallback);
        mPreviewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public  void Pause() {
        if(mCamera != null) {
            mCamera.setPreviewCallback(null);
        }
    }

    public void close() {
        try {
            mCamera.setPreviewCallback(null);
            if (mPreviewActive) {
                mCamera.stopPreview();
            }

            mPreviewActive = false;
            mCamera.release();
            mCamera = null;
        } catch (Exception e) {
            Log.e("@CameraClose", e.toString());
        }
    }

    public Bitmap getCurrentBitmap() {
        return mCurrentImage;
    }

    private static final class DetectionThread extends Thread {

        private byte[] data;
        private int width;
        private int height;

        public DetectionThread(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            if (!mProcessing.compareAndSet(false, true)) return;

            // Log.d(TAG, "BEGIN PROCESSING...");
            try {
                // Previous frame
                int[] pre = null;

                if (Preferences.SAVE_PREVIOUS) {
                    pre = mMotionDetector.getPrevious();
                }

                // Current frame (with changes)
                // long bConversion = System.currentTimeMillis();
                int[] img = null;
                if (Preferences.USE_RGB) {
                    img = ImageProcessing.decodeYUV420SPtoRGB(data, width, height);
                    mCurrentImage = ImageProcessing.rgbToBitmap(img, width, height);
                } else {
                    img = ImageProcessing.decodeYUV420SPtoLuma(data, width, height);
                    mCurrentImage = ImageProcessing.lumaToGreyscale(img, width, height);
                }
                // long aConversion = System.currentTimeMillis();
                // Log.d(TAG, "Converstion="+(aConversion-bConversion));

                // Current frame (without changes)
                int[] org = null;
                if (Preferences.SAVE_ORIGINAL && img != null) org = img.clone();

                if (img != null && mMotionDetector.detect(img, width, height)) {
                    // The delay is necessary to avoid taking a picture while in
                    // the
                    // middle of taking another. This problem can causes some
                    // phones
                    // to reboot.
                    long now = System.currentTimeMillis();
                    if (now > (mReferenceTime + Preferences.PICTURE_DELAY)) {
                        mReferenceTime = now;

                        Bitmap previous = null;
                        if (Preferences.SAVE_PREVIOUS && pre != null) {
                            if (Preferences.USE_RGB) {
                                previous = ImageProcessing.rgbToBitmap(pre, width, height);
                            }
                            else {
                                previous = ImageProcessing.lumaToGreyscale(pre, width, height);
                            }
                        }

                        Bitmap original = null;
                        if (org != null) {
                            if (Preferences.USE_RGB) {
                                original = ImageProcessing.rgbToBitmap(org, width, height);
                            }
                            else  {
                                original = ImageProcessing.lumaToGreyscale(org, width, height);
                            }
                        }

                        Bitmap bitmap = null;
                        if (Preferences.SAVE_CHANGES) {
                            if (Preferences.USE_RGB) {
                                bitmap = ImageProcessing.rgbToBitmap(img, width, height);
                            }
                            else {
                                bitmap = ImageProcessing.lumaToGreyscale(img, width, height);
                            }
                        }

                        Log.i(MainActivity.TAG, "Saving.. previous=" + previous + " original=" + original + " bitmap=" + bitmap);
                        Looper.prepare();
                    } else {
                        Log.i(MainActivity.TAG, "Not taking picture because not enough time has passed since the creation of the Surface");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mProcessing.set(false);
            }
            // Log.d(TAG, "END PROCESSING...");

            mProcessing.set(false);
        }
    };
}
