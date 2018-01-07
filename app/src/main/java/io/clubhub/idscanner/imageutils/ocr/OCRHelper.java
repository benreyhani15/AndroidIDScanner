package io.clubhub.idscanner.imageutils.ocr;

import android.app.ProgressDialog;
import com.googlecode.tesseract.android.TessBaseAPI;
import io.clubhub.idscanner.FileManager;
import io.clubhub.idscanner.ScannerActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by behnamreyhani-masoleh on 15-12-17.
 */
public class OCRHelper {
    private ScannerActivity mScannerActivity;
    private TessBaseAPI mTessAPI;
    private FileManager mFileManager;

    public OCRHelper(ScannerActivity activity, FileManager manager) {
        mScannerActivity = activity;
        mFileManager = manager;
    }

    public void setupOCRLibrary(){
        if (!isTessEnglishFileOnDevice()) {
            final ProgressDialog progressDialog = mScannerActivity.showProgressDialog("Downloading Image ...");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        getLanguageFile();
                    } catch (Exception e) {
                        e.printStackTrace();
                        showErrorMessage("Error", "There was a problem getting the required files.");
                    }
                    progressDialog.dismiss();
                }
            }).start();
        }

        if (!initOCR()) {
            showErrorMessage("Error", "There was a problem with the ID scanner.");
        }
    }

    public void deInitOCRLibrary(){
        if (mTessAPI != null) {
            mTessAPI.end();
        }
    }

    // TODO: improve accuracy by setting other tessbaseAPI params
    private boolean initOCR(){
        mTessAPI = new TessBaseAPI();
        try {
            if (!mTessAPI.init(mFileManager.getStorageDirectory().toString(), "eng")) {
                return false;
            }
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }

        mTessAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        mTessAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "`~!@#$%^&*_+=|\\}]{['\";:?>.<,");
        mTessAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST,
                "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-/()");
        return true;
    }

    private boolean isTessEnglishFileOnDevice() {
        try {
            return (new File(mFileManager.getStorageDirectory().toString() + File.separator + "tessdata"
                    + File.separator + "eng.traineddata")).exists();
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void getLanguageFile(){
        File tessDir = null;
        try {
            tessDir = new File(mFileManager.getStorageDirectory().toString()
                    + File.separator + "tessdata");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorMessage("Error", "Unable to install required files.");
            return;
        }

        // directory exists or is created, now unzip from assets zip file
        boolean installedFromAssets;
        if (tessDir!= null &&
                (tessDir.mkdir() || tessDir.isDirectory())) {
            installedFromAssets = unzipLanguageFileFromAssets();
            if (!installedFromAssets) {
                //TODO: if downloading language file fails from assets, get from the internet
            }
        } else {
            showErrorMessage("Error", "Unable to install required files.");
        }
    }

    private boolean unzipLanguageFileFromAssets() {
        try {
            ZipInputStream inputStream = new ZipInputStream(mScannerActivity.getAssets().open("eng.traineddata.zip"));
            ZipEntry entry = inputStream.getNextEntry();
            File destinationFile = new File(mFileManager.getStorageDirectory().toString()
                    + File.separator + "tessdata", "eng.traineddata");
            long zippedFileSize = entry.getSize();

            // Create a file output stream
            FileOutputStream outputStream = new FileOutputStream(destinationFile);
            final int BUFFER = 8192;

            // Buffer the output to the file
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream, BUFFER);

            // Write the contents
            int count = 0;
            byte[] data = new byte[BUFFER];
            while ((count = inputStream.read(data, 0, BUFFER)) != -1) {
                bufferedOutputStream.write(data, 0, count);
            }
            bufferedOutputStream.close();
            inputStream.closeEntry();
            inputStream.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void showErrorMessage(String title, String msg) {
        mScannerActivity.showErrorMessage(title, msg);
    }
}
