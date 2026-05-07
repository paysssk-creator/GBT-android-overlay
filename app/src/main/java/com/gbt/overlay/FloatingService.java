package com.gbt.overlay;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private View collapsedView;
    private View expandedView;
    private ImageView avatarView;
    private WebView webView;
    private WindowManager.LayoutParams params;
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isExpanded = false;
    private long lastInteractionTime;
    private static final String CHANNEL_ID = "GBT Overlay";
    private static final String PREFS_NAME = "GBTOverlayPrefs";
    private static final String WS_URL_KEY = "ws_url";
    private SpeechRecognizer speechRecognizer;
    private static final String[] WAKE_WORDS = {"小土豆", "豆豆"};
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingView();
        setupVoiceRecognition();
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "小土豆悬浮窗", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setContentTitle("小土豆");
        builder.setContentText("悬浮窗运行中");
        builder.setSmallIcon(android.R.drawable.ic_dialog_info);
        return builder.build();
    }
    private void createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay, null);
        collapsedView = floatingView.findViewById(R.id.collapsed_view);
        expandedView = floatingView.findViewById(R.id.expanded_view);
        avatarView = floatingView.findViewById(R.id.avatar);
        webView = floatingView.findViewById(R.id.webview);
        setupWebView();
        setupDragAndClick();
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;
        windowManager.addView(floatingView, params);
        lastInteractionTime = System.currentTimeMillis();
        startCollapseTimer();
    }
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });
        webView.loadUrl("https://gbtxiaotudou.com");
    }
    private void setupDragAndClick() {
        avatarView.setOnTouchListener(new View.OnTouchListener() {
            private long downTime;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long pressDuration = System.currentTimeMillis() - downTime;
                        if (pressDuration < 200 && Math.abs(event.getRawX() - initialTouchX) < 10 &&
                            Math.abs(event.getRawY() - initialTouchY) < 10) {
                            toggleExpansion();
                        }
                        return true;
                }
                return false;
            }
        });
    }
    private void toggleExpansion() {
        lastInteractionTime = System.currentTimeMillis();
        isExpanded = !isExpanded;
        if (isExpanded) {
            collapsedView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(floatingView, params);
        } else {
            collapsedView.setVisibility(View.VISIBLE);
            expandedView.setVisibility(View.GONE);
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            windowManager.updateViewLayout(floatingView, params);
        }
    }
    private void startCollapseTimer() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    if (isExpanded && System.currentTimeMillis() - lastInteractionTime > 30000) {
                        runOnUiThread(() -> {
                            if (isExpanded) {
                                toggleExpansion();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
    private void setupVoiceRecognition() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override
                public void onError(int error) {
                    restartListening();
                }
                @Override
                public void onResults(android.os.Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null) {
                        for (String text : matches) {
                            for (String wakeWord : WAKE_WORDS) {
                                if (text.contains(wakeWord)) {
                                    runOnUiThread(() -> {
                                        if (!isExpanded) {
                                            toggleExpansion();
                                        }
                                    });
                                    break;
                                }
                            }
                        }
                    }
                    restartListening();
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) {}
                @Override public void onEvent(int eventType, android.os.Bundle params) {}
            });
            startListening();
        }
    }
    private void startListening() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizer.startListening(intent);
        }
    }
    private void restartListening() {
        new Handler().postDelayed(() -> startListening(), 1000);
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && floatingView != null) {
            windowManager.removeView(floatingView);
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}