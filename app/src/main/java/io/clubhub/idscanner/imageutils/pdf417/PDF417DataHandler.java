package io.clubhub.idscanner.imageutils.pdf417;

import android.os.AsyncTask;

import io.clubhub.idscanner.imageutils.IDDictionary;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by benreyhani on 15-12-20.
 */
public class PDF417DataHandler extends AsyncTask<String, Boolean, Boolean> {
    private JSONObject mJSONObject;
    private PDF417Helper mHelper;

    public PDF417DataHandler(PDF417Helper helper) {
        mJSONObject = new JSONObject();
        mHelper = helper;
    }

    @Override
    protected Boolean doInBackground(String ... decodedResult) {
        Map<String, ArrayList<String>> dictionary =
                new LinkedHashMap<>(IDDictionary.getBarcodeIdDictionary());

        String [] values = decodedResult[0].split("\\r?\\n");
        boolean validityAlreadyReported = false;
        boolean isValid = false;

        for (String value : values) {
            
            //Denotes that all the required attributes have been set to JSON object
            if (isAllDataObtained() || isCancelled()) {
                break;
            }
            
            value = value.trim();
            // Assuming that all IDs being scanned have 3 letter trigger at the beginning like ON driver license
            if (value.length() > 3) {
            
                for (Map.Entry<String, ArrayList<String>> entry : dictionary.entrySet()) {
                    String key = entry.getKey();
                    
                    for (String trigger : dictionary.get(key)){
                        
                        if (value.substring(0, 3).equals(trigger)) {
                            
                            try {
                                // ON Driver's license has ',' at end of last name, fix for that
                                mJSONObject.put(key, key.equals(IDDictionary.LAST_NAME_KEY) ?
                                        value.substring(3, value.length() - 1) : value.substring(3));

                                if (canCheckIDValidity() && !validityAlreadyReported){
                                    validityAlreadyReported = true;
                                    isValid = isIDValid();
                                    publishProgress(isValid);
                                    
                                    if (!isValid) {
                                        // drop everything if its not valid id
                                        cancel(true);
                                    }
                                }
                                
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
        
        Boolean successfulDataExtract = isAllDataObtained();
        
        if (successfulDataExtract && !isCancelled() && isValid) {
            mJSONObject.remove(IDDictionary.ID_EXPIRY_DATE_KEY);
            mHelper.sendHTTPRequest(mJSONObject);
            mHelper.storeData(mJSONObject);
        }
        return successfulDataExtract;
    }

    @Override
    protected void onProgressUpdate(Boolean ... isValidID) {
        mHelper.reportIDValidity(isValidID[0]);
    }

    // Checks whether person is 19+, and if ID is not expired. Dates are stored in form: yyyyMMdd, also stores entrance time
    private boolean isIDValid(){
        SimpleDateFormat scannerDateFormat = new SimpleDateFormat(IDDictionary.ON_DRIVERS_LICENSE_DATE_FORMAT
                , Locale.getDefault());

        boolean valid;
        try {
            Calendar currentDate = Calendar.getInstance();
            storeCurrentTime(currentDate);

            Calendar expiryDate = Calendar.getInstance();
            expiryDate.setTime(
                    scannerDateFormat.parse(mJSONObject.getString(IDDictionary.ID_EXPIRY_DATE_KEY)));

            Calendar birthDate = Calendar.getInstance();
            birthDate.setTime(
                    scannerDateFormat.parse(mJSONObject.getString(IDDictionary.BIRTH_DATE_KEY)));

            int age = getAge(currentDate, birthDate);

            valid = expiryDate.after(currentDate) &&
                     age >= IDDictionary.ACTIVATED_DRINKING_AGE;

            mJSONObject.put("age", age);
        } catch (Exception e) {
            e.printStackTrace();
            valid = false;
        }
        return valid;
    }

    private int getAge(Calendar current, Calendar birth) {
        int age = current.get(Calendar.YEAR) - birth.get(Calendar.YEAR);

        if ((birth.get(Calendar.MONTH) > current.get(Calendar.MONTH))
                || (birth.get(Calendar.MONTH) == current.get(Calendar.MONTH) &&
                    birth.get(Calendar.DAY_OF_MONTH) > current.get(Calendar.DAY_OF_MONTH))){
            age--;
        }
        return age;
    }

    private boolean isAllDataObtained(){
        return mJSONObject.has(IDDictionary.ID_EXPIRY_DATE_KEY) && mJSONObject.has(IDDictionary.BIRTH_DATE_KEY)
                && mJSONObject.has(IDDictionary.FIRST_NAME_KEY) && mJSONObject.has(IDDictionary.LAST_NAME_KEY)
                    && mJSONObject.has(IDDictionary.GENDER_KEY) && mJSONObject.has(IDDictionary.ID_KEY);
    }

    private boolean canCheckIDValidity(){
        return mJSONObject.has(IDDictionary.BIRTH_DATE_KEY) && mJSONObject.has(IDDictionary.ID_EXPIRY_DATE_KEY);
    }

    private void storeCurrentTime(Calendar currentTime) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat(IDDictionary.CLUBHUB_STORING_DATE_FORMAT,
                Locale.getDefault());
        String formattedDate = dateFormat.format(currentTime.getTime());
        mJSONObject.put(IDDictionary.ENTRANCE_TIME_KEY, formattedDate);
    }
}
