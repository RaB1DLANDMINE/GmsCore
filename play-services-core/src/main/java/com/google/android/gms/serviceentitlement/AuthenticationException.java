/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.serviceentitlement;

import org.microg.safeparcel.AutoSafeParcelable;

/** TS.43 authentication error. Field ids/types reconstructed from GMS 26.44.32. */
public class AuthenticationException extends AutoSafeParcelable {
    @Field(1)
    public int i1;
    @Field(2)
    public int i2;
    @Field(3)
    public String s3;
    @Field(4)
    public String s4;
    @Field(5)
    public String s5;
    @Field(6)
    public int i6;

    public AuthenticationException() {
    }

    public AuthenticationException(int i1, int i2, String s3, String s4, String s5, int i6) {
        this.i1 = i1;
        this.i2 = i2;
        this.s3 = s3;
        this.s4 = s4;
        this.s5 = s5;
        this.i6 = i6;
    }

    public static final Creator<AuthenticationException> CREATOR = new AutoCreator<>(AuthenticationException.class);
}
