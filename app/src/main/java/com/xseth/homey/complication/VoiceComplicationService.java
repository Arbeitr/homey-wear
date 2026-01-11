package com.xseth.homey.complication;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

import com.xseth.homey.R;
import com.xseth.homey.voice.VoiceActivity;

import timber.log.Timber;

/**
 * Complication service for 1-tap voice activation
 */
public class VoiceComplicationService extends ComplicationDataSourceService {

    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationDataSourceService.ComplicationRequestListener listener) {
        Timber.d("Complication request: " + request.getComplicationType());

        try {
            ComplicationData complicationData = buildComplicationData(request.getComplicationType());
            listener.onComplicationData(complicationData);
        } catch (Exception e) {
            Timber.e(e, "Error building complication data");
            listener.onComplicationData(null);
        }
    }

    @Override
    public ComplicationType getPreviewData(ComplicationType type) {
        return type;
    }

    /**
     * Build complication data based on type
     */
    private ComplicationData buildComplicationData(ComplicationType type) {
        // Create intent to launch VoiceActivity
        Intent intent = new Intent(this, VoiceActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (type == ComplicationType.MONOCHROMATIC_IMAGE) {
            return new MonochromaticImageComplicationData.Builder(
                    new MonochromaticImage.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_mic_complication)
                    ).build(),
                    PlainComplicationText.Builder(getText(R.string.complication_label)).build()
            )
            .setTapAction(pendingIntent)
            .build();
        } else if (type == ComplicationType.SHORT_TEXT) {
            return new ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder("MIC").build(),
                    PlainComplicationText.Builder(getText(R.string.complication_label)).build()
            )
            .setTapAction(pendingIntent)
            .setMonochromaticImage(
                    new MonochromaticImage.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_mic_complication)
                    ).build()
            )
            .build();
        }

        return null;
    }
}
