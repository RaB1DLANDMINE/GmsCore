/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.serviceentitlement;

import org.microg.safeparcel.AutoSafeParcelable;

/** TS.43 service-entitlement request. Field ids/types reconstructed from GMS 26.44.32. */
public class GmsTs43ServiceEntitlementRequest extends AutoSafeParcelable {
    @Field(1)
    public int entitlementVersion;
    @Field(2)
    public String appId;
    @Field(3)
    public String s3;
    @Field(4)
    public String s4;
    @Field(5)
    public String s5;
    @Field(6)
    public String s6;
    @Field(7)
    public String s7;
    @Field(8)
    public String s8;
    @Field(9)
    public String s9;
    @Field(10)
    public String s10;
    @Field(11)
    public String s11;
    @Field(12)
    public String s12;
    @Field(13)
    public String s13;
    @Field(14)
    public String s14;

    public static final Creator<GmsTs43ServiceEntitlementRequest> CREATOR = new AutoCreator<>(GmsTs43ServiceEntitlementRequest.class);
}
