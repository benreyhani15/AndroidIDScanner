package io.clubhub.idscanner.imageutils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by benreyhani on 15-12-20.
 */
public class IDDictionary {
    // Keys for the JSON object that is being sent to server
    public static final String FIRST_NAME_KEY = "FN";
    public static final String LAST_NAME_KEY = "LN";
    public static final String ID_EXPIRY_DATE_KEY = "id_expiry_date";
    // For the value stored, 1 represents male, and 0 represents female
    public static final String GENDER_KEY = "Sex";
    public static final String BIRTH_DATE_KEY = "BD";
    // Value for this stored in form represented by CLUBHUB_STORING_DATE_FORMAT (below)
    public static final String ENTRANCE_TIME_KEY = "ET";
    public static final String ID_KEY = "ID";

    // In order of what comes first ON driver's license, assuming we only scan ON drivers license for now
    public static final String[] ID_EXPIRY_TRIGGERS = {"DBA"};
    public static final String[] LAST_NAME_TRIGGERS = {"DCS"};
    public static final String[] FIRST_NAME_TRIGGERS = {"DCT"};
    public static final String[] BIRTH_DAY_TRIGGERS = {"DBB"};
    public static final String[] GENDER_TRIGGERS = {"DBC"};
    public static final String[] ID_TRIGGERS = {"DAQ"};

    public static final String ON_DRIVERS_LICENSE_DATE_FORMAT = "yyyyMMdd";
    // This is what is stored for values in ON drivers license when the data is not available
    public static final String ON_DRIVERS_LICENSE_UNAVAILABLE_VALUE = "unavail";
    public static final String CLUBHUB_STORING_DATE_FORMAT = "yyyyMMddkkmmss";

    // Assume ON drinking age for now
    public static final int ACTIVATED_DRINKING_AGE = 19;

    private static final Map<String, ArrayList<String>> BARCODE_ID_DICTIONARY;

    static {
        Map<String, ArrayList<String>> idDict = new LinkedHashMap<>();

        idDict.put(ID_EXPIRY_DATE_KEY, getAttributeKeys(ID_EXPIRY_TRIGGERS));
        idDict.put(LAST_NAME_KEY, getAttributeKeys(LAST_NAME_TRIGGERS));
        idDict.put(FIRST_NAME_KEY, getAttributeKeys(FIRST_NAME_TRIGGERS));
        idDict.put(BIRTH_DATE_KEY, getAttributeKeys(BIRTH_DAY_TRIGGERS));
        idDict.put(GENDER_KEY, getAttributeKeys(GENDER_TRIGGERS));
        idDict.put(ID_KEY, getAttributeKeys(ID_TRIGGERS));

        BARCODE_ID_DICTIONARY = Collections.unmodifiableMap(idDict);
    }

    private static ArrayList<String> getAttributeKeys(String [] triggerArray){
        return new ArrayList<>(Arrays.asList(triggerArray));
    }

    public static Map<String, ArrayList<String>> getBarcodeIdDictionary() {
        return BARCODE_ID_DICTIONARY;
    }
}
