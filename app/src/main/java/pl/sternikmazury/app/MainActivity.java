package pl.sternikmazury.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 10;
    private WebView web;
    private LocationManager locationManager;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        requestLocation();
    }

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        startGps();
    }

    private void startGps() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 2, this);
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) sendLocation(last);
        } catch (SecurityException ignored) { }
    }

    private void sendLocation(Location location) {
        final String js = String.format(java.util.Locale.US,
            "window.onNativeLocation&&window.onNativeLocation(%.7f,%.7f,%.3f,%.2f,%d)",
            location.getLatitude(), location.getLongitude(), location.hasSpeed() ? location.getSpeed() : 0,
            location.hasBearing() ? location.getBearing() : 0, location.getTime());
        web.post(() -> web.evaluateJavascript(js, null));
    }

    @Override public void onLocationChanged(Location location) { sendLocation(location); }
    @Override public void onProviderEnabled(String provider) { startGps(); }
    @Override public void onProviderDisabled(String provider) { web.post(() -> web.evaluateJavascript("window.onGpsOff&&window.onGpsOff()", null)); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startGps();
        else web.post(() -> web.evaluateJavascript("window.onGpsDenied&&window.onGpsDenied()", null));
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.appBack&&window.appBack()", null);
    }

    public class Bridge {
        @JavascriptInterface public void vibrate(long milliseconds) {
            android.os.Vibrator v = (android.os.Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(milliseconds, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(milliseconds);
        }
    }
}
