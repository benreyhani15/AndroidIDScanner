package io.clubhub.idscanner.imageutils.pdf417;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.Log;

import io.clubhub.idscanner.imageutils.ImagePreProcessor;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Reader;

/**
 * Created by behnamreyhani-masoleh on 15-11-02.
 */
// Takes care of PDF417 decode using the zxing library
public class PDF417DecodeAsyncTask extends AsyncTask<Void, Void, Boolean> {
    private PDF417Helper mHelper;
    private byte[] mData;
    private Point mScreenRes;
    private Rect mFramingRect;
    private String mResponse;
    private int mCameraOrientation;

    public PDF417DecodeAsyncTask(byte[] data, PDF417Helper helper, Point screenRes,
                                 Rect framingRect, int cameraOrientation) {
        mData = data;
        mHelper = helper;
        mScreenRes = screenRes;
        mFramingRect = framingRect;
        mCameraOrientation = cameraOrientation;
    }

    @Override
    protected Boolean doInBackground(Void... values) {
        Bitmap pdf417Barcode = ImagePreProcessor.preProcessImageForPDF417(mData, mFramingRect, mScreenRes, mCameraOrientation);

        if (pdf417Barcode == null) {
            return Boolean.FALSE;
        }

        try {
            Result result = (new PDF417Reader()).decode(bitmapToBinaryBitmap(pdf417Barcode));
            if (result.getText() != null) {
                mResponse = result.getText();
                return Boolean.TRUE;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Boolean.FALSE;
        }
        return Boolean.FALSE;
    }

    protected void onPostExecute(Boolean successful) {
        mHelper.reportResult(successful, successful ? mResponse : null);
    }

    private BinaryBitmap bitmapToBinaryBitmap(Bitmap pdf417Bitmap) {
        int [] pixels = new int[pdf417Bitmap.getWidth()*pdf417Bitmap.getHeight()];
        pdf417Bitmap.getPixels(pixels, 0, pdf417Bitmap.getWidth(), 0, 0, pdf417Bitmap.getWidth(),
                pdf417Bitmap.getHeight());
        LuminanceSource source = new RGBLuminanceSource(pdf417Bitmap.getWidth(), pdf417Bitmap.getHeight(), pixels);
        return new BinaryBitmap(new HybridBinarizer(source));
    }
}
