/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.serviceentitlement;

import org.microg.safeparcel.AutoSafeParcelable;

/**
 * Result of {@code requestRawResponseForGetPhoneNumber}: the raw TS.43 entitlement-server response
 * ({@link #rawResponse}) plus timestamps, or an {@link AuthenticationException}. Field ids/types
 * reconstructed from GMS 26.44.32.
 */
public class TempTokenBundle extends AutoSafeParcelable {
    @Field(1)
    public String rawResponse;
    @Field(2)
    public AuthenticationException exception;
    @Field(3)
    public long issuedAtMs;
    @Field(4)
    public long expiresAtMs;

    public TempTokenBundle() {
    }

    public TempTokenBundle(String rawResponse, AuthenticationException exception, long issuedAtMs, long expiresAtMs) {
        this.rawResponse = rawResponse;
        this.exception = exception;
        this.issuedAtMs = issuedAtMs;
        this.expiresAtMs = expiresAtMs;
    }

    public static final Creator<TempTokenBundle> CREATOR = new AutoCreator<>(TempTokenBundle.class);
}
