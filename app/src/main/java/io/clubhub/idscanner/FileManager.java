package io.clubhub.idscanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import io.clubhub.idscanner.imageutils.IDDictionary;

import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by behnamreyhani-masoleh on 15-12-17.
 */
public class FileManager {
    private Context mContext;
    private ClubHubSharedPreferences mSharedPrefs;

    public FileManager(Context context) {
        mContext = context;
        mSharedPrefs = new ClubHubSharedPreferences();
    }

    private static File getExternalAlbumStorageDir(String albumName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), albumName);
        file.mkdirs();
        return file;
    }

    private static void saveBitmapToFile(Bitmap bitmap, File destFile) {
        FileOutputStream out = null;
        
        try {
            out = new FileOutputStream(destFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Used to save images that we're modified by image processing techniques to file; mostly for testing
    public static void savePicToExternalDirectory(Bitmap bitmap, String fileName) {
        saveBitmapToFile(bitmap, new File(getExternalAlbumStorageDir("clubhub"), fileName + ".png"));
    }

    public static void savePicToExternalDirectory(Mat mat, String fileName) {
        Bitmap outBM = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, outBM);
        savePicToExternalDirectory(outBM, fileName);
    }

    public File getStorageDirectory() throws Exception {
        File temp = null;
        
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())){
            temp = mContext.getExternalFilesDir(Environment.MEDIA_MOUNTED);
        }

        if (temp == null) {
            throw new Exception();
        }

        return temp;
    }

    public ClubHubSharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    public class ClubHubSharedPreferences {
        /* This shared pref is used to temporary hold CACHE_PREF_THRESHOLD scanned info before it
         gets pushed in bulk to the server once the threshold is reached.  If a network is not available
         it will go past the threshold, and will push to the server once a network connection is reached.
         This also holds the number of data info's in the cache.
        */
        public static final String SCANNED_INFO_CACHE_PREF = "scanned_info_cache";
        // Will attempt to push in bulk to the server when the value for this hits CACHE_PREF_THRESHOLD
        public static final String PEOPLE_IN_CACHE_COUNT_KEY = "people_in_cache_count_key";
        // Needs to be appended by the value of PEOPLE_IN_CACHE_COUNT_KEY
        public static final String CACHE_PERSON_KEY = "cache_person_key-";

        public static final int CACHE_PREF_THRESHOLD = 10;

        /* Stores the total # of people entered, and # of males from the Night.  This pref needs to
        be cleared after the end of the night scanning is over.  This information will be used to
        show to the bouncer, the guy/girl ratio and # of people he's let in on the UI.
        */
        public static final String NIGHT_DATA_PREF = "night_data_pref";
        public static final String TOTAL_PEOPLE_COUNT_KEY = "total_people_count_key";
        public static final String MALE_COUNT_KEY = "male_count_key";

        public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
            SharedPreferences nightData = mContext.getSharedPreferences(NIGHT_DATA_PREF,
                    Context.MODE_PRIVATE);
            nightData.registerOnSharedPreferenceChangeListener(listener);
        }

        public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
            SharedPreferences nightData = mContext.getSharedPreferences(NIGHT_DATA_PREF,
                    Context.MODE_PRIVATE);
            nightData.unregisterOnSharedPreferenceChangeListener(listener);
        }

        public synchronized void storeData(JSONObject jsonObject) {
            storeNightData(jsonObject);
            storeCacheData(jsonObject);
        }

        public void storeNightData(JSONObject jsonObject) {
            SharedPreferences nightData = mContext.getSharedPreferences(NIGHT_DATA_PREF,
                    Context.MODE_PRIVATE);
            int nightCount = nightData.getInt(TOTAL_PEOPLE_COUNT_KEY, 0);
            int maleCount = nightData.getInt(MALE_COUNT_KEY,0);

            SharedPreferences.Editor editor = nightData.edit();
            editor.putInt(TOTAL_PEOPLE_COUNT_KEY, ++nightCount);
            
            try {
                if (jsonObject.getString(IDDictionary.GENDER_KEY).equals("1")) {
                    editor.putInt(MALE_COUNT_KEY, ++maleCount);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            
            editor.commit();
        }

        public void storeCacheData(JSONObject jsonObject) {
            SharedPreferences cacheData = mContext.getSharedPreferences(SCANNED_INFO_CACHE_PREF,
                    Context.MODE_PRIVATE);
            int dataInCacheCount = cacheData.getInt(PEOPLE_IN_CACHE_COUNT_KEY, 0);
            SharedPreferences.Editor editor = cacheData.edit();

            editor.putInt(PEOPLE_IN_CACHE_COUNT_KEY, ++dataInCacheCount);
            editor.putString(CACHE_PERSON_KEY + dataInCacheCount, jsonObject.toString());
            editor.commit();

            if (dataInCacheCount >= CACHE_PREF_THRESHOLD) {
                // Need to push data to server (do this in this async task??)
            }
        }

        public String getCurrentNightStats(){
            SharedPreferences nightData = mContext.getSharedPreferences(NIGHT_DATA_PREF,
                    Context.MODE_PRIVATE);
            int totalCount = nightData.getInt(TOTAL_PEOPLE_COUNT_KEY, 0);
            int maleCount = nightData.getInt(MALE_COUNT_KEY, 0);
            
            if (totalCount == 0) {
                return "People Entered: 0";
            }
            
            double malePercentage = ((double)maleCount/totalCount) * 100;
            return "People Entered: " + totalCount + "\nMale: " + String.format("%.2f", malePercentage)
                    + "%, Female: " + String.format("%.2f", 100d-malePercentage) + "%";
        }
    }
}
