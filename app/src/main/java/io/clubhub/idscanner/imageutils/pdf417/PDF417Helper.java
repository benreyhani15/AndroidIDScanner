package io.clubhub.idscanner.imageutils.pdf417;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import io.clubhub.idscanner.FileManager;
import io.clubhub.idscanner.HTTPHelper;
import io.clubhub.idscanner.ScannerActivity;
import io.clubhub.idscanner.camera.CameraManager;

import org.json.JSONObject;

/**
 * Created by behnamreyhani-masoleh on 15-12-15.
 */
public class PDF417Helper {

    private static final int INITIAL_PICTURE_TAKE_DELAY = 50;
    private static final int PICTURE_TAKING_INTERVAL = 25;
    private static final int MAX_THREAD_COUNT = 4;

    private int mStartedThreadCount;
    private int mFinishedThreadCount;

    private boolean mScanningsCurrentlyInSession;
    private boolean mSuccessfullyDecoded;

    private ScannerActivity mScannerActivity;
    private CameraManager mCameraManager;
    private FileManager mFileManager;
    private HTTPHelper mHTTPHelper;

    private static final boolean DEBUG_DECODE = false;

    Camera.PictureCallback mCallback = new Camera.PictureCallback() {
    
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            mCameraManager.restartCamera();
            mStartedThreadCount++;
            startAsyncTaskInParallel(new PDF417DecodeAsyncTask(data, PDF417Helper.this,
                    mCameraManager.getScreenRes(), mCameraManager.getFramingRect(),
                    mCameraManager.getCameraOrienation()));
    
            if (mStartedThreadCount < MAX_THREAD_COUNT) {
               takePictureWithDelay(PICTURE_TAKING_INTERVAL);
            }
        }
    };

    public PDF417Helper(ScannerActivity activity, FileManager fileManager) {
        mScannerActivity = activity;
        mCameraManager = mScannerActivity.getCameraManager();
        mFileManager = fileManager;
        mHTTPHelper = new HTTPHelper(mScannerActivity);
    }

    public boolean currentlyScanning(){
        return mScanningsCurrentlyInSession;
    }

    public void decodeBatch(){
        mScanningsCurrentlyInSession = true;
        takePictureWithDelay(INITIAL_PICTURE_TAKE_DELAY);
    }

    private void takePictureWithDelay(long delay){
        Handler h = new Handler();
        Runnable delayed = new Runnable() {
            @Override
            public void run() {
                mCameraManager.takePicture(mCallback);
            }
        };
        h.postDelayed(delayed, delay);
    }

    private void startAsyncTaskInParallel(PDF417DecodeAsyncTask task) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        else {
            task.execute();
        }
    }
    
    public synchronized void reportResult(boolean successful, String result) {
        mFinishedThreadCount++;
        if (successful && !mSuccessfullyDecoded) {
            mSuccessfullyDecoded = true;
            if (DEBUG_DECODE) {
                mScanningsCurrentlyInSession = false;
                // Shows scanned result to UI, only for testing barcode purposes
                mScannerActivity.reportScannerBatchResponse(mSuccessfullyDecoded, result);
            } else {
                // Extract useful data, check validity of license, and cache data
                new PDF417DataHandler(this).execute(result);
            }
        }

        if (mFinishedThreadCount == MAX_THREAD_COUNT) {

            if (!mSuccessfullyDecoded) {
                mScannerActivity.reportScannerBatchResponse(false, null);
            }

            mScanningsCurrentlyInSession = false;
            resetBatchState();
        }
    }

    public synchronized void reportIDValidity(boolean valid) {
        mScanningsCurrentlyInSession = false;
        mScannerActivity.reportIDValidity(valid);
    }

    private void resetBatchState(){
        mSuccessfullyDecoded = false;
        mFinishedThreadCount = 0;
        mStartedThreadCount = 0;
    }

    public void storeData(JSONObject object){
        mFileManager.getSharedPrefs().storeData(object);
    }

    public void sendHTTPRequest(JSONObject jsonObject) {
        mHTTPHelper.sendScanData(jsonObject);
    }
}
