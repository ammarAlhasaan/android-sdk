/************************************************************************
 * Copyright PointCheckout, Ltd.
 */
package com.pc.android.sdk;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.v7.app.AlertDialog;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Objects;

/**
 * @author pointcheckout
 */
public class PointCheckoutClient {
    /**
     * Specifies whether the environment is test or production
     */
    private Environment environment;
    /**
     * Auto closes the modal on payment success or failure
     */
    private boolean autoDismiss;
    /**
     * The main modal that will show the payment page
     */
    private AlertDialog modal;
    /**
     * Indicates if whether the client is initialized or not
     */
    private boolean initialized;

    /**
     * @throws PointCheckoutException if the environment is null
     */
    public PointCheckoutClient() throws PointCheckoutException {
        this(Environment.PRODUCTION, true);

    }

    /**
     * @param autoDismiss auto close the modal on payment success or failure
     * @throws PointCheckoutException if the environment is null
     */
    public PointCheckoutClient(boolean autoDismiss) throws PointCheckoutException {
        this(Environment.PRODUCTION, autoDismiss);
    }

    /**
     * @param environment specifies whether the environment is test or production
     * @param autoDismiss auto close the modal on payment success or failure
     * @throws PointCheckoutException if the environment is null
     */
    public PointCheckoutClient(Environment environment, boolean autoDismiss) throws PointCheckoutException {
        PointCheckoutUtils.assertNotNull(environment);
        this.environment = environment;
        this.autoDismiss = autoDismiss;
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
     * @param checkoutKey of the payment
     * @return checkout url
     */
    private String getCheckoutUrl(String checkoutKey) {
        return String.format(environment.getUrl() + "/pay-mobile?checkoutKey=%s", checkoutKey);
    }

    /**
     * @param context     of the activity to showing the modal
     * @param checkoutKey of the payment
     * @param listener    to be called when the modal gets dismissed
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
                            .setTitle("Error")
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

    /**
     * @param context     of the activity to showing the modal
     * @param checkoutKey of the payment
     * @param listener    to be called when the modal gets dismissed
     */
    private void payUnsafe(
            final Context context,
            final String checkoutKey,
            final PointCheckoutEventListener listener) {

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.launchUrl(context, Uri.parse(getCheckoutUrl(checkoutKey)));
    }

    /**
     * Dismiss the modal if autoDismiss is true
     *
     * @param listener to be called whether the modal is dismissed or not
     */
    private void requestDismiss(PointCheckoutEventListener listener) throws PointCheckoutException {
        if (autoDismiss)
            dismiss();

        if (Objects.nonNull(listener))
            listener.onDismiss();


    }

    /**
     * Dismisses the modal
     *
     * @throws PointCheckoutException if the modal is already dismissed
     */
    public void dismiss() throws PointCheckoutException {

        if (!modal.isShowing())
            throw new PointCheckoutException("Already dismissed");

        CookieManager.getInstance().removeAllCookies(null);
        modal.dismiss();
    }
}
