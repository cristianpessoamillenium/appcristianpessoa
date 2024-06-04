package com.sherdle.universal.util.layout;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.sherdle.universal.R;
import com.sherdle.universal.util.Log;

public class PrivacyBottomSheet extends BottomSheetDialogFragment {

    private static final String PRIVACY_PREF_STORAGE = "PrivacyPreferences";
    private static final String PRIVACY_STORAGE_KEY = "not_agreed_yet";

    @Deprecated
    public static void showPrivacySheetIfNeeded(AppCompatActivity context){
        if (context.getResources().getString(R.string.privacy_policy_url).length() == 0) return;

        SharedPreferences settings = context.getSharedPreferences(PRIVACY_PREF_STORAGE, 0);
        if (settings.getBoolean(PRIVACY_STORAGE_KEY, true)) {
            PrivacyBottomSheet bottomSheet = new PrivacyBottomSheet();
            bottomSheet.show(context.getSupportFragmentManager(), bottomSheet.getTag());
            bottomSheet.setCancelable(false);
        }
    }

    public static void askForUMPIfNeeded(AppCompatActivity context) {
        // Set tag for under age of consent. false means users are not under age
        // of consent.
        ConsentRequestParameters params = new ConsentRequestParameters
                .Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(context);
        consentInformation.requestConsentInfoUpdate(
                context,
                params,
                (ConsentInformation.OnConsentInfoUpdateSuccessListener) () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            context,
                            (ConsentForm.OnConsentFormDismissedListener) loadAndShowError -> {
                                if (loadAndShowError != null) {
                                    // Consent gathering failed.
                                    Log.w("UMP", String.format("%s: %s",
                                            loadAndShowError.getErrorCode(),
                                            loadAndShowError.getMessage()));
                                }

                                // Consent has been gathered.
                                if (consentInformation.canRequestAds()) {
                                    //TODO suboptimal UX
                                   // context.recreate();
                                    Log.i("ADS", "Constate: Hi Hi HI");
                                } else {
                                    Log.i("ADS", "Constate: No");

                                }
                            }
                    );
                },
                (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                    // Consent gathering failed.
                    Log.w("UMP", String.format("%s: %s",
                            requestConsentError.getErrorCode(),
                            requestConsentError.getMessage()));
                });

        // Check if you can initialize the Google Mobile Ads SDK in parallel
        // while checking for new consent information. Consent obtained in
        // the previous session can be used to request ads.
        if (consentInformation.canRequestAds()) {
            //initializeMobileAdsSdk();
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottom_sheet_privacy, container, false);

        TextView summaryView = v.findViewById(R.id.privacy_summary_text);
        String summary = getResources().getString(R.string.privacy_policy_summary,  getResources().getString(R.string.privacy_policy_url));
        Spanned result = HtmlCompat.fromHtml(summary,HtmlCompat.FROM_HTML_MODE_LEGACY);
        summaryView.setText(result);
        summaryView.setMovementMethod(LinkMovementMethod.getInstance());

        Button button = v.findViewById(R.id.privacy_accept_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences settings = getContext().getSharedPreferences(PRIVACY_PREF_STORAGE, 0);
                settings.edit().putBoolean(PRIVACY_STORAGE_KEY, false).apply();
                dismiss();
            }
        });

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

}
