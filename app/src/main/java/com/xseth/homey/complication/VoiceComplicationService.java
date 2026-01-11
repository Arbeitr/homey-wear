package com.xseth.homey.complication;

import android.app.PendingIntent;
import android.content.Intent;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationManager;
import android.support.wearable.complications.ComplicationProviderService;
import android.support.wearable.complications.ComplicationText;

import com.xseth.homey.R;
import com.xseth.homey.voice.VoiceActivity;

import timber.log.Timber;

/**
 * Complication service for 1-tap voice activation
 */
public class VoiceComplicationService extends ComplicationProviderService {

    @Override
    public void onComplicationUpdate(int complicationId, int dataType, ComplicationManager complicationManager) {
        Timber.d("Complication update: " + dataType);

        try {
            ComplicationData complicationData = buildComplicationData(dataType);
            if (complicationData != null) {
                complicationManager.updateComplicationData(complicationId, complicationData);
            } else {
                complicationManager.noUpdateRequired(complicationId);
            }
        } catch (Exception e) {
            Timber.e(e, "Error building complication data");
            complicationManager.noUpdateRequired(complicationId);
        }
    }

    /**
     * Build complication data based on type
     */
    private ComplicationData buildComplicationData(int type) {
        // Create intent to launch VoiceActivity
        Intent intent = new Intent(this, VoiceActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (type == ComplicationData.TYPE_ICON) {
            return new ComplicationData.Builder(ComplicationData.TYPE_ICON)
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_mic_complication))
                    .setTapAction(pendingIntent)
                    .build();
        } else if (type == ComplicationData.TYPE_SHORT_TEXT) {
            return new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText("MIC"))
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_mic_complication))
                    .setTapAction(pendingIntent)
                    .build();
        }

        return null;
    }
}
