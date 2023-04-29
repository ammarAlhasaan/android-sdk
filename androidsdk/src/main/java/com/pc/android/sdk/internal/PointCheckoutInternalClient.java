/************************************************************************
 * Copyright PointCheckout, Ltd.
 */
package com.pc.android.sdk.internal;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.pc.android.androidsdk.R;
import com.pc.android.sdk.Environment;
import com.pc.android.sdk.PointCheckoutEventListener;
import com.pc.android.sdk.PointCheckoutException;

/**
 * @author pointcheckout
 */
public class PointCheckoutInternalClient {
    /**
     * The main modal that will show the payment page
     */
    private AlertDialog modal;
    /**
     * Indicates if whether the client is initialized or not
     */
    private boolean initialized;
    /**
     *
     */
    private Environment environment;

    /**
     * @throws PointCheckoutException if the environment is null
     */
    public PointCheckoutInternalClient(Environment environment) throws PointCheckoutException {
        PointCheckoutUtils.assertNotNull(environment);
        this.environment = environment;
    }

    public void initialize(Context context) {
        PointCheckoutUtils.evaluateSafetyNetAsync(context, new PointCheckoutSafetyNetListener() {
            @Override
            public void callback(boolean valid, String message) {
                initialized = valid;
            }
        });
    }

    /**
     * @param context  of the activity to showing the modal
     * @param listener to be called when the modal gets dismissed
     * @throws PointCheckoutException if the context or checkoutKey is null
     */
    public void pay(
            final Context context,
            final String checkoutKey,
            final PointCheckoutEventListener listener) throws PointCheckoutException {

        PointCheckoutUtils.assertNotNull(context);
        PointCheckoutUtils.assertNotNull(checkoutKey);

        if (initialized) {
            payUnsafe(context, checkoutKey, listener);
            return;
        }


        PointCheckoutUtils.evaluateSafetyNet(context, new PointCheckoutSafetyNetListener() {
            @Override
            public void callback(boolean valid, String message) {

                if (!valid) {
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.pointcheckout_error)
                            .setMessage(message)
                            .setNegativeButton(android.R.string.no, null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                    return;
                }
                initialized = valid;
                payUnsafe(context, checkoutKey, listener);

            }
        });
    }


    private void payUnsafe(
            final Context context,
            final String checkoutKey,
            final PointCheckoutEventListener listener) {

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        WebView webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.loadUrl(getPaymentUrl(checkoutKey));
        webView.setFocusableInTouchMode(true);
        webView.setFocusable(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String request) {
                view.loadUrl(request);

                if (isDone(request)) {
                    try {
                        dismiss();
                        if (listener != null)
                            listener.onUpdate();
                    } catch (PointCheckoutException e) {
                        e.printStackTrace();
                    }
                }


                return true;
            }
        });

        alert.setNegativeButton(R.string.pointcheckout_close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                try {
                    dismiss();
                    if (listener != null)
                        listener.onDismiss();
                } catch (PointCheckoutException e) {
                    e.printStackTrace();
                }
            }
        });


        LinearLayout wrapper = new LinearLayout(context);
        EditText keyboardHack = new EditText(context);

        keyboardHack.setVisibility(View.GONE);

        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(webView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        wrapper.addView(keyboardHack, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        alert.setView(wrapper);

        modal = alert.show();

    }

    /**
     * Dismisses the modal
     *
     * @throws PointCheckoutException if the modal is already dismissed
     */
    public void dismiss() throws PointCheckoutException {

        if (!modal.isShowing())
            throw new PointCheckoutException("Already dismissed");

        CookieManager.getInstance().removeAllCookie();
        modal.dismiss();
    }

    private boolean isDone(String url) {
        String COMPLETE = "/complete/";
        String SUCCESS = "/success-redirect";
        String CONFIRMATION = "/payment-confirmation";

        return ((url.startsWith(environment.getUrl()) || url.startsWith(environment.getPointCheckoutUrl()))
        && (url.contains(COMPLETE) || url.contains(SUCCESS) || url.contains(CONFIRMATION)));
    }

    private String getPaymentUrl(String checkoutKey) {
        return this.environment.getUrl() + "/embedded/checkout/" + checkoutKey;
    }
}
