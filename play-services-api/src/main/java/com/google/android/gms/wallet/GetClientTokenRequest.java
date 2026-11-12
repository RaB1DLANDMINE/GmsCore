/*
 * SPDX-FileCopyrightText: 2025 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.wallet;

import android.os.Parcel;

import androidx.annotation.NonNull;

import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableCreatorAndWriter;
import com.google.android.gms.wallet.firstparty.WalletCustomTheme;

@SafeParcelable.Class
public class GetClientTokenRequest extends AbstractSafeParcelable {
    @Field(2)
    public WalletCustomTheme theme;
    @Field(3)
    public boolean facilitatedPayments;
    @Field(4)
    public int clientType = 1;

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }

    public static final SafeParcelableCreatorAndWriter<GetClientTokenRequest> CREATOR = findCreator(GetClientTokenRequest.class);

}
