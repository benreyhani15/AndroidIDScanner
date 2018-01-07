package io.clubhub.idscanner.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import java.io.IOException;

/**
 * Created by behnamreyhani-masoleh on 15-10-23.
 */
public class CameraManager {
    public static final int BC_FRAME_WIDTH = 448 * 3;
    public static final int BC_FRAME_HEIGHT = 100 * 3;
    public static final int BC_LIGHT_FRAME_OFFSET_RATIO = 10 ;
    public static final int OCR_FRAME_WIDTH = 325 * 3;
    public static final int OCR_FRAME_HEIGHT = 200 * 3;

    private CameraConfigManager mCameraConfig;
    private static Camera mCamera = null;

    private boolean mIsLightOn;
    private boolean mCameraIsInitialized;
    private boolean mIsPreviewing;
    private Rect mFramingRect;

    public CameraManager() {
        mCameraConfig = new CameraConfigManager();
    }

    public int getCameraOrienation() { return mCameraConfig.getCameraOrientation(); }

    public synchronized void initCamera(SurfaceHolder holder, boolean turnLightOn,
                                        Context context) throws IOException {
        if (mCamera != null) {
            return;
        }

        try {
            mCamera = Camera.open();
        } catch (Exception e){
            e.printStackTrace();
        }
        
        if (mCamera == null) {
            throw new IOException();
        }

        mCamera.setPreviewDisplay(holder);
        
        if (!mCameraIsInitialized) {
            mCameraConfig.initFromCameraParams(mCamera, context);
            mCameraIsInitialized = true;
        }
        
        mCameraConfig.setCameraDefaultParams(mCamera);
        setLightMode(turnLightOn);
        mCamera.startPreview();
        mIsPreviewing = true;
    }

    public synchronized void deInitCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mIsPreviewing = false;
            mCamera.release();
            mCamera = null;
            mFramingRect = null;
        }
    }

    public boolean setLightMode(boolean turnLightOn) {
       // mCameraConfig.setSceneMode(mCamera, true, turnLightOn);
        if (mCameraConfig.setCameraLightParams(mCamera, turnLightOn)) {
            mIsLightOn = turnLightOn;
            return true;
        }
        return false;
    }

    public boolean isPictureTakingReady() {
        return mCamera != null && mIsPreviewing;
    }

    public synchronized void adjustFramingRect(boolean isForOCR) {
        if (!mCameraIsInitialized) {
            return;
        }

        // Move the rectangle to the bottom of the screen if light is on to avoid shine
        Point screenRes = mCameraConfig.getScreenRes();
        int newWidth = isForOCR ? OCR_FRAME_WIDTH : BC_FRAME_WIDTH;
        int newHeight = isForOCR ? OCR_FRAME_HEIGHT : BC_FRAME_HEIGHT;
        int leftOffset = (screenRes.x - newWidth) / 2;
        int topOffset = mIsLightOn ? (screenRes.y - newHeight) - (screenRes.y/BC_LIGHT_FRAME_OFFSET_RATIO)
                : (screenRes.y - newHeight) / 2;

        mFramingRect = new Rect(leftOffset, topOffset,
                leftOffset + newWidth, topOffset + newHeight);
        mCameraConfig.setFocusAndMeteringArea(mCamera, mFramingRect);
       // mCameraConfig.setSceneMode(mCamera, isForOCR, mIsLightOn);
    }

    public synchronized Rect getFramingRect() {
        return mFramingRect;
    }

    public void takePicture(Camera.PictureCallback callback) {
        if (mCamera != null) {
            mCamera.takePicture(null, null, callback);
        }
    }

    public void restartCamera(){
        if (mCamera != null) {
            mIsPreviewing = true;
            mCamera.startPreview();
        }
    }

    public Camera getCamera(){
        return mCamera;
    }

    public Point getCameraRes() {
        return mCameraConfig.getCameraRes();
    }

    public Point getScreenRes() {
        return mCameraConfig.getScreenRes();
    }

    public boolean isLightOn() {
        return mIsLightOn;
    }
}
