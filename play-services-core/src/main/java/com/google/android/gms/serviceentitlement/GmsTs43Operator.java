/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.serviceentitlement;

import java.util.List;

import org.microg.safeparcel.AutoSafeParcelable;

/** TS.43 operator config. Field ids/types reconstructed from GMS 26.44.32. */
public class GmsTs43Operator extends AutoSafeParcelable {
    @Field(1)
    public String s1;
    @Field(2)
    public int i2;
    @Field(3)
    public List<String> list3;
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
    @Field(15)
    public List<String> list15;
    @Field(16)
    public String s16;
    @Field(17)
    public String s17;
    @Field(18)
    public String s18;
    @Field(19)
    public String s19;
    @Field(20)
    public String s20;
    @Field(21)
    public String s21;
    @Field(22)
    public String s22;
    @Field(23)
    public String s23;

    public static final Creator<GmsTs43Operator> CREATOR = new AutoCreator<>(GmsTs43Operator.class);
}
