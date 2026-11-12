/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.serviceentitlement;

import android.net.Network;

import org.microg.safeparcel.AutoSafeParcelable;

/** TS.43 carrier config. Field ids/types reconstructed from GMS 26.44.32. */
public class GmsTs43CarrierConfig extends AutoSafeParcelable {
    @Field(1)
    public String serverUrl;
    @Field(2)
    public String s2;
    @Field(3)
    public boolean b3;
    @Field(4)
    public int i4;
    @Field(5)
    public String s5;
    @Field(6)
    public Network network;

    public static final Creator<GmsTs43CarrierConfig> CREATOR = new AutoCreator<>(GmsTs43CarrierConfig.class);
}
