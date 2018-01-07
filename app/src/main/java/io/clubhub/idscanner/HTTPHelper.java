package io.clubhub.idscanner;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import io.clubhub.idscanner.imageutils.IDDictionary;

/**
 * Created by benreyhani on 2017-03-13.
 */
public class HTTPHelper {
    public static final String SCANS_URL = "http://ec2-54-146-241-77.compute-1.amazonaws.com:3000/scans";
    private Context mContext;

    public HTTPHelper(Context context) {
        mContext = context;
    }

    public void sendScanData(JSONObject jsonObject) {
        RequestQueue requestQueue = Volley.newRequestQueue(mContext);
        JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.POST, SCANS_URL, convertToServerSchema(jsonObject),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        });
        // Add the request to the RequestQueue.
        requestQueue.add(jsonRequest);
    }

/* Needs to be in this format
    {
        date_of_birth: Date,
        gender: String,
        age: Number,
        club: String
    }
    */

    private JSONObject convertToServerSchema(JSONObject original) {
        JSONObject converted = new JSONObject();
        
        try {
            converted.put("date_of_birth", convertDate(original.getString(IDDictionary.BIRTH_DATE_KEY)));
            converted.put("gender", original.get(IDDictionary.GENDER_KEY).equals("1") ? "M" : "F");
            converted.put("age", original.getInt("age"));
            converted.put("club", "DC");
        } catch (Exception E) {

        }
        
        return converted;
    }

    // YYYYMMDD to YYYY-MM-DD
    private String convertDate(String date) {
        return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6);
    }

    public void getScanData() {
        RequestQueue requestQueue = Volley.newRequestQueue(mContext);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, SCANS_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        });
        // Add the request to the RequestQueue.
        requestQueue.add(stringRequest);
    }
}
