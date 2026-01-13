package com.xseth.homey.voice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.xseth.homey.R;
import com.xseth.homey.homey.HomeyAPI;

import java.util.ArrayList;

import timber.log.Timber;

/**
 * Main voice activity with SpeechRecognizer
 */
public class VoiceActivity extends FragmentActivity {

    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;
    private static final int AUTO_CLOSE_DELAY_MS = 1500;

    private enum State {
        INITIALIZING,
        LISTENING,
        PROCESSING,
        SUCCESS,
        ERROR
    }

    private SpeechRecognizer speechRecognizer;
    private ImageView micIcon;
    private TextView statusText;
    private TextView commandText;
    private ProgressBar progressBar;
    private Button retryButton;
    private State currentState;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);

        micIcon = findViewById(R.id.mic_icon);
        statusText = findViewById(R.id.status_text);
        commandText = findViewById(R.id.command_text);
        progressBar = findViewById(R.id.progress_bar);
        retryButton = findViewById(R.id.retry_button);

        handler = new Handler(Looper.getMainLooper());

        retryButton.setOnClickListener(v -> startVoiceRecognition());

        setState(State.INITIALIZING);

        // Check for audio permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_RECORD_AUDIO);
        } else {
            initializeSpeechRecognizer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeSpeechRecognizer();
            } else {
                setState(State.ERROR);
                statusText.setText(R.string.voice_permission_denied);
            }
        }
    }

    /**
     * Initialize speech recognizer
     */
    private void initializeSpeechRecognizer() {
        try {
            // Try to create on-device speech recognizer (requires API 30+)
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                Timber.d("Using on-device speech recognition");
            } else {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                Timber.d("Using cloud-based speech recognition");
            }

            speechRecognizer.setRecognitionListener(new VoiceRecognitionListener());
            startVoiceRecognition();
        } catch (Exception e) {
            Timber.e(e, "Failed to initialize speech recognizer");
            setState(State.ERROR);
            statusText.setText(R.string.voice_error_generic);
        }
    }

    /**
     * Start voice recognition
     */
    private void startVoiceRecognition() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer();
            return;
        }

        setState(State.LISTENING);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Timber.e(e, "Failed to start listening");
            setState(State.ERROR);
            statusText.setText(R.string.voice_error_generic);
        }
    }

    /**
     * Set current state and update UI
     */
    private void setState(State state) {
        currentState = state;
        runOnUiThread(() -> updateUIForState(state));
    }

    /**
     * Update UI based on state
     */
    private void updateUIForState(State state) {
        switch (state) {
            case INITIALIZING:
                micIcon.setImageResource(R.drawable.ic_mic);
                statusText.setText(R.string.voice_initializing);
                commandText.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.GONE);
                break;

            case LISTENING:
                micIcon.setImageResource(R.drawable.ic_mic_active);
                micIcon.startAnimation(android.view.animation.AnimationUtils.loadAnimation(
                        this, R.anim.pulse));
                statusText.setText(R.string.voice_listening);
                commandText.setVisibility(View.VISIBLE);
                commandText.setText("");
                progressBar.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                break;

            case PROCESSING:
                micIcon.clearAnimation();
                micIcon.setImageResource(R.drawable.ic_mic);
                statusText.setText(R.string.voice_processing);
                progressBar.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.GONE);
                break;

            case SUCCESS:
                micIcon.clearAnimation();
                micIcon.setImageResource(R.drawable.ic_check);
                progressBar.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                // Auto-close after delay
                handler.postDelayed(this::finish, AUTO_CLOSE_DELAY_MS);
                break;

            case ERROR:
                micIcon.clearAnimation();
                micIcon.setImageResource(R.drawable.ic_mic);
                progressBar.setVisibility(View.GONE);
                retryButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Process recognized speech
     */
    private void processCommand(String command) {
        setState(State.PROCESSING);
        commandText.setText(command);

        // Execute command in background thread
        new Thread(() -> {
            try {
                HomeyAPI api = HomeyAPI.getAPI();
                VoiceCommandExecutor executor = new VoiceCommandExecutor(api);
                
                ParsedIntent intent = GermanIntentParser.parse(command);
                VoiceCommandExecutor.Result result = executor.execute(intent);

                runOnUiThread(() -> {
                    if (result.isSuccess()) {
                        setState(State.SUCCESS);
                        statusText.setText(result.getMessage());
                    } else {
                        setState(State.ERROR);
                        statusText.setText(result.getMessage());
                    }
                });
            } catch (Exception e) {
                Timber.e(e, "Error processing command");
                runOnUiThread(() -> {
                    setState(State.ERROR);
                    statusText.setText(R.string.voice_error_generic);
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Recognition listener implementation
     */
    private class VoiceRecognitionListener implements RecognitionListener {

        @Override
        public void onReadyForSpeech(Bundle params) {
            Timber.d("Ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            Timber.d("Beginning of speech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Could use this for visual feedback
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // Not used
        }

        @Override
        public void onEndOfSpeech() {
            Timber.d("End of speech");
        }

        @Override
        public void onError(int error) {
            Timber.e("Speech recognition error: " + error);
            
            String errorMessage;
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    errorMessage = getString(R.string.voice_audio_error);
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    errorMessage = getString(R.string.voice_no_match);
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    errorMessage = getString(R.string.voice_network_error);
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    errorMessage = getString(R.string.voice_timeout);
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    errorMessage = getString(R.string.voice_client_error);
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    errorMessage = getString(R.string.voice_busy_error);
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    errorMessage = getString(R.string.voice_permission_denied);
                    break;
                default:
                    errorMessage = getString(R.string.voice_error_generic);
                    break;
            }

            runOnUiThread(() -> {
                setState(State.ERROR);
                statusText.setText(errorMessage);
            });
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            
            if (matches != null && !matches.isEmpty()) {
                String recognizedText = matches.get(0);
                Timber.d("Recognized: " + recognizedText);
                processCommand(recognizedText);
            } else {
                runOnUiThread(() -> {
                    setState(State.ERROR);
                    statusText.setText(R.string.voice_no_match);
                });
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            
            if (matches != null && !matches.isEmpty()) {
                String partialText = matches.get(0);
                runOnUiThread(() -> commandText.setText(partialText));
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // Not used
        }
    }
}
