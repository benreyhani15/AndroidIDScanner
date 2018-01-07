package io.clubhub.idscanner.imageutils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;

import io.clubhub.idscanner.FileManager;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by behnamreyhani-masoleh on 15-10-18.
 */
// Takes care of image pre-processing using the opencv library
public class ImagePreProcessor {
    //private static final double IMAGE_SCALE_FACTOR = 0.50;

    public static final int WIDTH_BUFFER_RATIO = 14;
    public static final int HEIGHT_BUFFER_RATIO = 14;
    private static final int TEXT_BOX_CROP_PIXEL_BUFFER = 20;
    private static final double WHITE_PIXEL_THRESHOLD = 0.15;
    private static final double WHITE_PIXEL_THRESHOLD_TWO = 0.15;

    private static byte [] rotateImageFromByteArray(byte [] data, int rotationDegrees) {
        Matrix mat = new Matrix();
        mat.postRotate(rotationDegrees);

        Bitmap tmp = byteArrayToBitmap(data);
        Bitmap rotated =  Bitmap.createBitmap(tmp, 0, 0, tmp.getWidth(), tmp.getHeight(), mat, true);
        return bitmapToByteArray(rotated);
    }

    private static Bitmap byteArrayToBitmap(byte [] data) {
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    private static byte [] bitmapToByteArray(Bitmap bm) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public static Bitmap preProcessImageForPDF417(byte [] data, Rect frame, Point screenRes,
                                                  int cameraOrientation) {
        Bitmap bm = null;
        try {
            // Fix for Nexus 5X bug
            if (Build.MODEL.equals("Nexus 5X")) {
                // rotate image 180 degrees
                data = rotateImageFromByteArray(data, 180);
            }

            bm = getCroppedBitmapFromData(data, frame, screenRes);

            Mat greyscaledMat = convertMatToGrayScale(bm, cameraOrientation);

            Mat blurredAdaptive = getBlurredBWUsingAdaptive(greyscaledMat);

            bm = cropForPDF417(blurredAdaptive, bm);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return bm;
    }


    public static List<Bitmap> preProcessImageForOCR(byte [] data, Rect frame, Point screenRes) {
        Bitmap bm;
        List<Bitmap> textBoxList = null;
        try {
            bm = getCroppedBitmapFromData(data, frame, screenRes);
            //  bm = getTestImage(true);

            Mat greyscaledMat = convertMatToGrayScale(bm);

            Mat bwForOCR  = convertToBinaryAdaptiveThreshold(greyscaledMat, 21, 20, true);

            // Pre-processing for text box recognition
            Mat bwForTextBoxRecognition = getBlurredBWUsingAdaptive(greyscaledMat);
            //Mat bwForTextBoxRecognition = getBlurredBWUsingCannyEdge(greyscaledMat);

            textBoxList = findTextBoxes(bwForTextBoxRecognition, bwForOCR);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return textBoxList;
    }

    private static boolean isTextRect(org.opencv.core.Rect rect, Mat org) {
        return (rect.width > rect.height) && rect.height > 10 && rect.width > 50
                && rect.height < org.height()/1.5 && rect.width < org.width()/1.5;
    }

    private static boolean canBePDF417Rect(org.opencv.core.Rect rect, Mat org) {
        return (rect.width > 3*rect.height) && (rect.width >= 0.6*org.width());
    }

    private static Mat getBlurredBWUsingAdaptive(Mat grey) {
        Mat bw = convertToBinaryAdaptiveThreshold(grey, 13, 10, false);
        return blurImageForTextBoxRecognition(bw);
    }

    private static Bitmap cropForPDF417(Mat blurredMat, Bitmap bm) {
        Mat org = new Mat();
        Utils.bitmapToMat(bm, org);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(blurredMat, contours, hierarchy, Imgproc.RETR_CCOMP,
                Imgproc.CHAIN_APPROX_SIMPLE);

        // Indicates that no barcode was found in pre-processing, send message of error back early
        if (hierarchy.empty()) {
            return null;
        }

        org.opencv.core.Rect pdfRect = null;
        
        for (int idx = 0; idx>=0; idx = (int) hierarchy.get(0,idx)[0]) {
            org.opencv.core.Rect rect = Imgproc.boundingRect(contours.get(idx));
            // assuming the biggest rect in the image is the pdf417 one and getting it
            if (canBePDF417Rect(rect,org)) {
                // for testing only
                if (pdfRect == null || pdfRect.area() < rect.area()) {
                    pdfRect = rect;
                }
            }
        }

        if (pdfRect == null) {
            return null;
        }

        return getBitmapFromOpenCVRect(org, pdfRect);
    }

    private static Bitmap getBitmapFromOpenCVRect(Mat org, org.opencv.core.Rect rect) {
        Mat buffered = addBufferToTextBoxMat(org, rect);
        Bitmap outBM = Bitmap.createBitmap(buffered.cols(), buffered.rows(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(buffered, outBM);
        return outBM;
    }

    private static List<Bitmap> findTextBoxes(Mat blurredMat, Mat org) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(blurredMat, contours, hierarchy, Imgproc.RETR_CCOMP,
                Imgproc.CHAIN_APPROX_SIMPLE);
        Mat mask = Mat.zeros(blurredMat.size(), CvType.CV_8UC1);

        List<Bitmap> textBoxes = new ArrayList<>();
        // For testing boxes found
        Mat tmp = new Mat();
        org.copyTo(tmp);
        int count = 0;
        int countH = 0;
        
        for (int idx = 0; idx>=0; idx =(int) hierarchy.get(0, idx)[0]) {
            countH++;
            org.opencv.core.Rect rect = Imgproc.boundingRect(contours.get(idx));
            
            if (isTextRect(rect, org)) {
                Mat cropped = new Mat(org, rect);
                double r1 = Core.countNonZero(cropped)/rect.area();
                
                if (r1 >= WHITE_PIXEL_THRESHOLD) {
                    Mat maskROI = new Mat(mask, rect);
                    maskROI.setTo(new Scalar(0, 0, 0));
                    Imgproc.drawContours(mask, contours, idx, new Scalar(255, 255, 255), 5);
                    double r = (double)Core.countNonZero(maskROI) / rect.area();
                    
                    if (r >= WHITE_PIXEL_THRESHOLD_TWO) {
                        Mat bufferedMat = addBufferToTextBoxMat(org, rect);

                        // TODO: see which order is faster
                        FileManager.savePicToExternalDirectory(bufferedMat, "TextBox-" + idx);
                        //  rescaleMat(bufferedMat, IMAGE_SCALE_FACTOR);
                        Imgproc.rectangle(tmp, rect.br(), rect.tl(), new Scalar(0, 0, 0), 10);

                        Bitmap outBM = Bitmap.createBitmap(bufferedMat.cols(), bufferedMat.rows(),
                                Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(bufferedMat, outBM);
                        textBoxes.add(outBM);
                        count++;
                    }
                }
            }
        }
        FileManager.savePicToExternalDirectory(tmp, "Boxed");
        return textBoxes;
    }

    private static Mat addBufferToTextBoxMat(Mat org, org.opencv.core.Rect rect) {
        Mat shallowCopy = new Mat();
        org.copyTo(shallowCopy);

        Mat originalBox = new Mat(shallowCopy, rect);
        
        try {
            rect.x-=TEXT_BOX_CROP_PIXEL_BUFFER;
            rect.y-=TEXT_BOX_CROP_PIXEL_BUFFER;
            rect.width+=2*TEXT_BOX_CROP_PIXEL_BUFFER;
            rect.height+=2*TEXT_BOX_CROP_PIXEL_BUFFER;
            return new Mat(shallowCopy, rect);
        } catch (Exception e) {
            return originalBox;
        }
    }

    private static Mat blurImageForTextBoxRecognition(Mat mat) {
        Mat blurred = new Mat();
        mat.copyTo(blurred);

        //TODO: Play around with the params for opening and closing, only for recognizing text boxes **
        // First get rid of background noise pixels
        Mat rectKernel = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(1, 11));
        Imgproc.morphologyEx(blurred, blurred, Imgproc.MORPH_OPEN, rectKernel);
        //saveIntermediateInPipelineToFile(blurred,"Opened");

        // Close white image pixels to get white boxes
        Mat closeKernel = Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(80, 1));
        Imgproc.morphologyEx(blurred, blurred, Imgproc.MORPH_CLOSE, closeKernel);
       // saveIntermediateInPipelineToFile(blurred, "Closed");
        return blurred;
    }

    private static Mat convertMatToGrayScale(Bitmap org) {
        return convertMatToGrayScale(org, 0);
    }

    private static Mat convertMatToGrayScale(Bitmap org, int cameraOrientation) {
        Mat grayScaled = new Mat();
        Mat orgMat = new Mat();
        Utils.bitmapToMat(org, orgMat);

        Imgproc.cvtColor(orgMat, grayScaled, Imgproc.COLOR_BGR2GRAY);
        return grayScaled;
    }

    private static Mat convertToBinaryAdaptiveThreshold(Mat greyscaledMat, int blockSize, int i, boolean isBlackOnWhite) {
        Mat binaryMat = new Mat();
        Imgproc.adaptiveThreshold(greyscaledMat, binaryMat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                isBlackOnWhite ? Imgproc.THRESH_BINARY : Imgproc.THRESH_BINARY_INV, blockSize, i);
        return binaryMat;
    }

    private static Bitmap getCroppedBitmapFromData(byte [] data, Rect frame, Point screenRes) throws Exception {
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length, false);
        int height = decoder.getHeight();
        int width = decoder.getWidth();

        double heightBuffer = (double) screenRes.y/HEIGHT_BUFFER_RATIO;
        double widthBuffer = (double) screenRes.x/WIDTH_BUFFER_RATIO;

        Double left = ((double)frame.left/screenRes.x)*width - widthBuffer;
        Double top =  ((double)frame.top/screenRes.y)*height - heightBuffer;
        Double right = ((double)frame.right/screenRes.x)*width + widthBuffer;
        Double bottom = ((double)frame.bottom/screenRes.y)*height + heightBuffer;

        return decoder.decodeRegion(new Rect(left.intValue(), top.intValue(),
                right.intValue(), bottom.intValue()), null);
    }

}
