/*
 * SPDX-FileCopyrightText: 2023 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.tapandpay.internal.firstparty;

import android.os.Parcel;
import androidx.annotation.NonNull;
import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableCreatorAndWriter;
import org.microg.gms.utils.ToStringHelper;

/**
 * Request for {@code ITapAndPayService#firstPartyTokenizePan} (transaction id 3) — the manual
 * "add a card" / tokenize-PAN flow driven by {@code TokenizePanChimeraActivity}.
 *
 * The SafeParcel {@code @Field} ids and types are reconstructed verbatim from the GMS 26.44.32
 * ({@code 264432035}) {@code writeToParcel} layout, so the parcel is binary compatible with the
 * first-party Wallet client that produces it. Field names are derived from the GMS handler
 * ({@code gnpv.b(Context)}); where the semantics could not be confirmed the original obfuscated
 * letter is kept as the name suffix and noted in a comment. Ids 4, 8, 10 are absent in the stock
 * layout.
 */
@SafeParcelable.Class
public class FirstPartyTokenizePanRequest extends AbstractSafeParcelable {
    /** stock {@code d} — id of the card/token to tokenize (empty to add a new card). */
    @Field(2)
    public String clientTokenId;
    /** stock {@code e} */
    @Field(3)
    public boolean b3;
    /** stock {@code f} — opaque payload forwarded to the tokenize UI. */
    @Field(5)
    public byte[] by5;
    /** stock {@code g} */
    @Field(6)
    public boolean b6;
    /** stock {@code h} — calling package context (compared against the GMS package). */
    @Field(7)
    public String callingPackage;
    /** stock {@code i} */
    @Field(9)
    public boolean b9;
    /** stock {@code j} */
    @Field(11)
    public String s11;
    /** stock {@code k} */
    @Field(12)
    public boolean b12;
    /** stock {@code l} — tokenization entry point. */
    @Field(13)
    public int entryPoint;
    /** stock {@code m} */
    @Field(14)
    public String s14;
    /** stock {@code n} — unified-tokenization session token. */
    @Field(15)
    public String s15;
    /** stock {@code o} */
    @Field(16)
    public int i16;
    /** stock {@code p} (deprecated in GMS) */
    @Field(17)
    public boolean b17;
    /** stock {@code q} */
    @Field(18)
    public boolean b18;
    /** stock {@code r} */
    @Field(19)
    public byte[] by19;
    /** stock {@code s} */
    @Field(20)
    public boolean b20;
    /** stock {@code t} */
    @Field(21)
    public String s21;
    /** stock {@code u} — target wallet id. */
    @Field(22)
    public String walletId;
    /** stock {@code v} — caller opts in to unified tokenization. */
    @Field(23)
    public boolean enableUnifiedTokenization;
    /** stock {@code w} */
    @Field(24)
    public byte[] by24;
    /** stock {@code x} — serialized logging/notification metadata proto. */
    @Field(25)
    public byte[] loggingNotificationMetadata;
    /** stock {@code y} */
    @Field(26)
    public boolean b26;

    @NonNull
    @Override
    public String toString() {
        return ToStringHelper.name("FirstPartyTokenizePanRequest")
                .field("clientTokenId", clientTokenId)
                .field("b3", b3)
                .field("by5", by5)
                .field("b6", b6)
                .field("callingPackage", callingPackage)
                .field("b9", b9)
                .field("s11", s11)
                .field("b12", b12)
                .field("entryPoint", entryPoint)
                .field("s14", s14)
                .field("s15", s15)
                .field("i16", i16)
                .field("b17", b17)
                .field("b18", b18)
                .field("by19", by19)
                .field("b20", b20)
                .field("s21", s21)
                .field("walletId", walletId)
                .field("enableUnifiedTokenization", enableUnifiedTokenization)
                .field("by24", by24)
                .field("loggingNotificationMetadata", loggingNotificationMetadata)
                .field("b26", b26)
                .end();
    }

    public static final SafeParcelableCreatorAndWriter<FirstPartyTokenizePanRequest> CREATOR = findCreator(FirstPartyTokenizePanRequest.class);

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        CREATOR.writeToParcel(this, dest, flags);
    }
}
