/*
 * Copyright (C) 2013-2017 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.wallet;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.wallet.GetClientTokenRequest;
import com.google.android.gms.wallet.GetClientTokenResponse;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.internal.IOwService;
import com.google.android.gms.wallet.internal.IWalletServiceCallbacks;
import org.microg.gms.common.GooglePackagePermission;
import org.microg.gms.utils.ExtendedPackageInfo;
import org.microg.gms.tapandpay.backend.PaymentsClientTokenFactory;

public class OwServiceImpl extends IOwService.Stub {
    private static final String TAG = "GmsWalletOwSvc";
    private Context context;
    private final String packageName;

    public OwServiceImpl(Context context, String packageName) {
        this.context = context;
        this.packageName = packageName;
    }

    @Override
    public void isReadyToPay(IsReadyToPayRequest request, Bundle args, IWalletServiceCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "isReadyToPay: " + request.toJson());
        try {
            callbacks.onIsReadyToPayResponse(Status.SUCCESS, false, Bundle.EMPTY);
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    @Override
    public void getClientToken(GetClientTokenRequest getClientTokenRequest, Bundle options, IWalletServiceCallbacks callbacks) throws RemoteException {
        boolean firstParty = packageName.equals(context.getPackageName()) ||
                new ExtendedPackageInfo(context, packageName).hasGooglePackagePermission(GooglePackagePermission.WALLET);
        try {
            if (!firstParty || getClientTokenRequest == null || getClientTokenRequest.facilitatedPayments ||
                    (getClientTokenRequest.clientType != 1 && getClientTokenRequest.clientType != 2)) {
                callbacks.onClientTokenReceived(new Status(CommonStatusCodes.DEVELOPER_ERROR,
                        "Unsupported Payments client-token request"), new GetClientTokenResponse(), Bundle.EMPTY);
                return;
            }
            byte[] token = PaymentsClientTokenFactory.create(context, getClientTokenRequest.clientType);
            callbacks.onClientTokenReceived(Status.SUCCESS, new GetClientTokenResponse(token), Bundle.EMPTY);
        } catch (Exception e) {
            Log.w(TAG, "getClientToken failed: " + e.getClass().getSimpleName());
            callbacks.onClientTokenReceived(Status.INTERNAL_ERROR, new GetClientTokenResponse(), Bundle.EMPTY);
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (super.onTransact(code, data, reply, flags)) return true;
        Log.d(TAG, "onTransact [unknown]: " + code + ", " + data + ", " + flags);
        return false;
    }
}
