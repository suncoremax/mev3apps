package com.miron.electronics.stock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View splashScreen;
    private ProgressBar progressBar;
    private static final String APP_URL = "https://mev5.vercel.app/";

    // ── Location permission (needed for navigator.geolocation inside the WebView) ──
    private static final int LOCATION_PERM_REQUEST_CODE = 1001;
    private String pendingGeoOrigin = null;
    private GeolocationPermissions.Callback pendingGeoCallback = null;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        splashScreen = findViewById(R.id.splash_screen);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);

        // Ask for the OS-level location permission early, so it's already
        // granted by the time the web app calls navigator.geolocation
        // (punch-in, live location ping, "nearest shop", etc.)
        requestLocationPermissionIfNeeded();

        // Pull-to-refresh colour matches Miron brand (blue)
        swipeRefreshLayout.setColorSchemeResources(
                R.color.miron_blue,
                R.color.miron_gold
        );

        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
        });

        setupWebView();

        if (isNetworkAvailable()) {
            webView.loadUrl(APP_URL);
        } else {
            showNoInternetDialog();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " MironElectronicsApp/1.0");

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                swipeRefreshLayout.setRefreshing(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                // Hide splash after first load
                if (splashScreen.getVisibility() == View.VISIBLE) {
                    new Handler().postDelayed(() -> {
                        splashScreen.animate().alpha(0f).setDuration(400).withEndAction(() ->
                                splashScreen.setVisibility(View.GONE)
                        ).start();
                    }, 600);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                if (request.isForMainFrame()) {
                    showNoInternetDialog();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            // Allow camera/mic if the web app requests it
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

            // ── THE FIX: without this override, WebView silently denies every
            // navigator.geolocation call from the web app's JS, no matter what
            // permissions are declared in the manifest or granted at the OS level.
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    // OS permission already granted — tell the WebView yes,
                    // and remember this choice for this origin (don't ask again).
                    callback.invoke(origin, true, false);
                } else {
                    // Ask the user for the OS-level permission now; the result
                    // is delivered to onRequestPermissionsResult() below, which
                    // then completes this same callback.
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{ Manifest.permission.ACCESS_FINE_LOCATION,
                                          Manifest.permission.ACCESS_COARSE_LOCATION },
                            LOCATION_PERM_REQUEST_CODE);
                }
            }

            @Override
            public void onGeolocationPermissionsHidePrompt() {
                pendingGeoOrigin = null;
                pendingGeoCallback = null;
            }

            // Handle JS alert/confirm/prompt dialogs properly
            @Override
            public boolean onJsAlert(WebView view, String url, String message,
                                     android.webkit.JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Miron Electronics")
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                                       android.webkit.JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Miron Electronics")
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setNegativeButton("Cancel", (d, w) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, android.webkit.JsPromptResult result) {
                final android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                input.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(message)
                        .setView(input)
                        .setPositiveButton("OK", (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton("Cancel", (d, w) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    // ── Location permission helpers ─────────────────────────────────────
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION,
                                  Manifest.permission.ACCESS_COARSE_LOCATION },
                    LOCATION_PERM_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERM_REQUEST_CODE) return;

        boolean granted = hasLocationPermission();

        // If the WebView was mid-way through a navigator.geolocation call
        // (onGeolocationPermissionsShowPrompt fired first), complete it now.
        if (pendingGeoCallback != null && pendingGeoOrigin != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoOrigin = null;
            pendingGeoCallback = null;
        }
    }

    private void showNoInternetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("Please check your internet connection and try again.")
                .setPositiveButton("Retry", (d, w) -> {
                    if (isNetworkAvailable()) {
                        webView.loadUrl(APP_URL);
                    } else {
                        showNoInternetDialog();
                    }
                })
                .setNegativeButton("Exit", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    // Bridge class so web app JS can call Android methods if needed
    public static class AndroidBridge {
        private final Context context;

        AndroidBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public String getAppVersion() {
            return "1.0.0";
        }

        @JavascriptInterface
        public boolean isAndroidApp() {
            return true;
        }
    }
}
