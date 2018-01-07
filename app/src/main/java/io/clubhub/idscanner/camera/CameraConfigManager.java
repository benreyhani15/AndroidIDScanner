package io.clubhub.idscanner.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import io.clubhub.idscanner.imageutils.ImagePreProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by behnamreyhani-masoleh on 15-10-23.
 */
public class CameraConfigManager {
    private static final int MIN_PREVIEW_PIXELS = 470 * 320;
    private static final int MAX_PREVIEW_PIXELS = 800 * 600;

    private Point mScreenRes;
    private Point mCameraRes;
    private int mCameraOrientation;

    void initFromCameraParams(Camera camera, Context context) {
        Camera.Parameters params = camera.getParameters();
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();

        // In case it thinks its in portrait
        if (width < height) {
            int temp = width;
            width = height;
            height = temp;
        }
        
        mScreenRes = new Point(width, height);
        mCameraRes = findBestPreviewSizeValue(params);

        if (params.isVideoStabilizationSupported()) {
            params.setVideoStabilization(true);
        }

        // Fix for messed up camera orientation on new phones,
        // Needs testing on other phones other than Nexus 5X
        setCorrectDisplayOrientation(windowManager,camera);

        camera.setParameters(params);
    }

    public void setCorrectDisplayOrientation(WindowManager windowManager,
                                                    android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(0, info);
        mCameraOrientation = info.orientation;
        int rotation = windowManager.getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (mCameraOrientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (mCameraOrientation - degrees + 360) % 360;
        }
        
        camera.setDisplayOrientation(result);
    }

    public int getCameraOrientation() {
        return mCameraOrientation;
    }

    void setCameraDefaultParams(Camera camera) {
        Camera.Parameters params = camera.getParameters();
        String focusMode = findSettableValue(params.getSupportedFocusModes(),
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
                Camera.Parameters.FOCUS_MODE_AUTO,
                Camera.Parameters.FOCUS_MODE_MACRO,
                Camera.Parameters.FOCUS_MODE_EDOF);
        
        if (focusMode != null) {
            params.setFocusMode(focusMode);
        }

        params.set("orientation", "landscape");
        params.setPreviewSize(mCameraRes.x, mCameraRes.y);
        camera.setParameters(params);
    }

    void setFocusAndMeteringArea(Camera camera, Rect focusAreaRect) {
        Camera.Parameters params = camera.getParameters();
        List<Camera.Area> convertedCameraFocusArea = convertToCameraParamsFocusArea(focusAreaRect);

        if (params.getMaxNumFocusAreas() > 0) {
            params.setFocusAreas(convertedCameraFocusArea);
        }

        if (params.getMaxNumMeteringAreas() > 0) {
            params.setMeteringAreas(convertedCameraFocusArea);
        }
        
        camera.setParameters(params);
    }

    boolean setCameraLightParams(Camera camera, boolean turnLightOn) {
        Camera.Parameters params = camera.getParameters();

        String torch = null;
        
        if (turnLightOn) {
            torch = findSettableValue(params.getSupportedFlashModes(),
                    Camera.Parameters.FLASH_MODE_TORCH,
                    Camera.Parameters.FLASH_MODE_ON);
        } else {
            torch = findSettableValue(params.getSupportedFlashModes(),
                    Camera.Parameters.FLASH_MODE_OFF);
        }

        if (torch != null) {
            params.setFlashMode(torch);
            // TODO: Might be better compensated outdoors, need to test if accuracy is bad
            //setBestExposure(params, turnLightOn);
            camera.setParameters(params);
            return true;
        }
        
        return false;
    }

    private static String findSettableValue(Collection<String> supportedValues,
                                            String... desiredValues) {
        String result = null;
        
        if (supportedValues != null) {
            for (String desiredValue : desiredValues) {
                if (supportedValues.contains(desiredValue)) {
                    result = desiredValue;
                    break;
                }
            }
        }
        
        return result;
    }

    private List<Camera.Area> convertToCameraParamsFocusArea(Rect focusArea) {
        List<Camera.Area>list = new ArrayList<>();

        double heightBuffer = (double) mScreenRes.y/ ImagePreProcessor.HEIGHT_BUFFER_RATIO;
        double widthBuffer = (double) mScreenRes.x/ImagePreProcessor.WIDTH_BUFFER_RATIO;

        Double left = ((double)focusArea.left/mScreenRes.x)*2000 - widthBuffer;
        Double top =  ((double)focusArea.top/mScreenRes.y)*2000 - heightBuffer;
        Double right = ((double)focusArea.right/mScreenRes.x)*2000 + widthBuffer;
        Double bottom = ((double)focusArea.bottom/mScreenRes.y)*2000 + heightBuffer;
        Camera.Area area = new Camera.Area(new Rect( left.intValue() - 1000, top.intValue() - 1000,
                right.intValue() - 1000, bottom.intValue() - 1000),1);
        list.add(area);
        return list;
    }

    public Point getScreenRes() {
        return mScreenRes;
    }

    public Point getCameraRes() {
        return mCameraRes;
    }

    private Point findBestPreviewSizeValue(Camera.Parameters parameters) {
        // Sort by size, descending
        List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(parameters.getSupportedPreviewSizes());
        
        Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        StringBuilder previewSizesString = new StringBuilder();
        
        for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
            previewSizesString.append(supportedPreviewSize.width).append('x')
                    .append(supportedPreviewSize.height).append(' ');
        }

        Point bestSize = null;
        float screenAspectRatio = (float) mScreenRes.x / (float) mScreenRes.y;

        float diff = Float.POSITIVE_INFINITY;
        for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;
            int pixels = realWidth * realHeight;
            
            if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
                continue;
            }
            
            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            
            if (maybeFlippedWidth == mScreenRes.x && maybeFlippedHeight == mScreenRes.y) {
                Point exactPoint = new Point(realWidth, realHeight);
                return exactPoint;
            }
            
            float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
            float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            
            if (newDiff < diff) {
                bestSize = new Point(realWidth, realHeight);
                diff = newDiff;
            }
        }

        if (bestSize == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            bestSize = new Point(defaultSize.width, defaultSize.height);
        }

        return bestSize;
    }
}
