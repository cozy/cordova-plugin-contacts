package org.apache.cordova.contacts.authenticator;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/*
 * Implement AbstractAccountAuthenticator and stub out all
 * of its methods
 */
public class StubAuthenticator extends AbstractAccountAuthenticator {
    Context mContext;
    // Simple constructor
    public StubAuthenticator(Context context) {
        super(context);
        mContext = context;
    }
    // Editing properties is not supported
    @Override
    public Bundle editProperties(
            AccountAuthenticatorResponse r, String s) {
        throw new UnsupportedOperationException();
    }
    // Don't add additional accounts
    @Override
    public Bundle addAccount(
            AccountAuthenticatorResponse r,
            String s,
            String s2,
            String[] strings,
            Bundle bundle) throws NetworkErrorException {
        // Redirect to main activity


        //TODO : Z hard dependance on cozy-mobile !
        Intent intent = new Intent(mContext, io.cozy.files_client.MainActivity.class);
        // But generic way would need CATEGORY_DEFAULT in manifest to work.
        // Intent intent = new Intent();
        // intent.setAction(Intent.ACTION_MAIN);
        // intent.addCategory(Intent.CATEGORY_LAUNCHER);
        // intent.setPackage(mContext.getPackageName());

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }
    // Ignore attempts to confirm credentials
    @Override
    public Bundle confirmCredentials(
            AccountAuthenticatorResponse r,
            Account account,
            Bundle bundle) throws NetworkErrorException {
        return null;
    }
    // Getting an authentication token is not supported
    @Override
    public Bundle getAuthToken(
            AccountAuthenticatorResponse r,
            Account account,
            String s,
            Bundle bundle) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }
    // Getting a label for the auth token is not supported
    @Override
    public String getAuthTokenLabel(String s) {
        throw new UnsupportedOperationException();
    }
    // Updating user credentials is not supported
    @Override
    public Bundle updateCredentials(
            AccountAuthenticatorResponse r,
            Account account,
            String s, Bundle bundle) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }
    // Checking features for the account is not supported
    @Override
    public Bundle hasFeatures(
        AccountAuthenticatorResponse r,
        Account account, String[] features) throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }
}
