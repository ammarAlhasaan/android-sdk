/************************************************************************
 * Copyright PointCheckout, Ltd.
 */
package com.pc.android.sdk.internal;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.pc.android.androidsdk.R;
import com.pc.android.sdk.PointCheckoutException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author pointcheckout
 */
public class PointCheckoutUtils {
    public static void assertNotNull(Object obj) throws PointCheckoutException {
        if (obj == null)
            throw new PointCheckoutException(String.format("%s can not be null", obj.getClass().getSimpleName()));
    }

    public static void evaluateSafetyNetAsync(final Context context, final PointCheckoutSafetyNetListener listener) {

        try {

            SafetyNet.getClient(context).attest(generateNonce(), "AIzaSyDcQMrv-SM5vvPBiaSrmrFlEVo2HPFmh3I")
                    .addOnSuccessListener(new OnSuccessListener<SafetyNetApi.AttestationResponse>() {
                        @Override
                        public void onSuccess(SafetyNetApi.AttestationResponse attestationResponse) {
                            listener.callback(evaluateJws(attestationResponse.getJwsResult()), context.getString(R.string.pointcheckout_not_secure));
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            e.printStackTrace();
                            listener.callback(false, context.getString(R.string.pointcheckout_not_secure));
                        }
                    });

        } catch (NoClassDefFoundError exception) {
            listener.callback(!isDeviceRooted(), context.getString(R.string.pointcheckout_not_secure));
        }


    }

    public static void evaluateSafetyNet(final Context context, final PointCheckoutSafetyNetListener listener) {

        final ProgressDialog dialog = ProgressDialog.show(context, "",
                context.getString(R.string.pointcheckout_checking_device), true);
        dialog.show();

        try {

            SafetyNet.getClient(context).attest(generateNonce(), "AIzaSyDcQMrv-SM5vvPBiaSrmrFlEVo2HPFmh3I")
                    .addOnSuccessListener(new OnSuccessListener<SafetyNetApi.AttestationResponse>() {
                        @Override
                        public void onSuccess(SafetyNetApi.AttestationResponse attestationResponse) {
                            dialog.dismiss();
                            listener.callback(evaluateJws(attestationResponse.getJwsResult()), context.getString(R.string.pointcheckout_not_secure));
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            dialog.dismiss();
                            e.printStackTrace();
                        }
                    });
        } catch (NoClassDefFoundError exception) {
            dialog.dismiss();
            listener.callback(!isDeviceRooted(), context.getString(R.string.pointcheckout_not_secure));
        }

    }

    private static boolean evaluateJws(String jwsResult) {

        try {
            Map<String, Object> values = jsonToMap(decodeJws(jwsResult));
            return Boolean.valueOf(values.get("ctsProfileMatch").toString()) && Boolean.valueOf(values.get("ctsProfileMatch").toString());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }


    }

    private static String decodeJws(String jwsResult) {

        if (jwsResult == null) {
            return null;
        }
        final String[] jwtParts = jwsResult.split("\\.");
        if (jwtParts.length == 3) {
            String decodedPayload = new String(Base64.decode(jwtParts[1], Base64.DEFAULT));
            return decodedPayload;
        } else {
            return null;
        }
    }

    private static byte[] generateNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    private static Map<String, Object> jsonToMap(String json) throws JSONException {
        Map<String, Object> retMap = new HashMap<>();

        if (json != null) {
            retMap = toMap(new JSONObject(json));
        }

        return retMap;
    }

    private static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<>();

        Iterator<String> keysItr = object.keys();
        while (keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    private static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }


    /**
     * @return true if device is rooted and false otherwise
     */
    private static boolean isDeviceRooted() {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3();
    }

    private static boolean checkRootMethod1() {
        String buildTags = android.os.Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static boolean checkRootMethod2() {
        String[] paths = {"/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkRootMethod3() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"/system/xbin/which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

}
