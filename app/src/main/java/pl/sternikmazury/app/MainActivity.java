package pl.sternikmazury.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    TextView status, position, speed, course, trip;
    Button anchor, startTrip, sos;
    LocationManager lm;
    Location anchorLocation, lastLocation;
    boolean anchorOn=false, tripOn=false;
    float tripMeters=0;
    long tripStart=0;
    static final int REQ_LOC=10, REQ_CALL=11;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); position=findViewById(R.id.position);
        speed=findViewById(R.id.speed); course=findViewById(R.id.course); trip=findViewById(R.id.trip);
        anchor=findViewById(R.id.anchor); startTrip=findViewById(R.id.startTrip); sos=findViewById(R.id.sos);
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);

        startTrip.setOnClickListener(v -> {
            tripOn=!tripOn;
            if(tripOn){ tripMeters=0; tripStart=System.currentTimeMillis(); startTrip.setText("ZAKOŃCZ REJS"); trip.setText("Dziennik: rejs trwa"); }
            else { startTrip.setText("START REJSU"); updateTrip(); }
        });
        anchor.setOnClickListener(v -> {
            if(lastLocation==null){ Toast.makeText(this,"Najpierw poczekaj na pozycję GPS.",Toast.LENGTH_LONG).show(); return; }
            anchorOn=!anchorOn;
            if(anchorOn){ anchorLocation=new Location(lastLocation); anchor.setText("WYŁĄCZ ALARM KOTWICZNY"); Toast.makeText(this,"Alarm ustawiony: promień 40 m",Toast.LENGTH_SHORT).show(); }
            else { anchorLocation=null; anchor.setText("USTAW ALARM KOTWICZNY 40 m"); }
        });
        sos.setOnClickListener(v -> call984());
        requestLocation();
    }

    void requestLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOC); return;
        }
        startGps();
    }
    void startGps(){
        try{
            if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){ status.setText("GPS jest wyłączony — włącz lokalizację w telefonie."); startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return; }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,1,this);
            status.setText("GPS: aktywny");
        } catch(SecurityException e){ status.setText("Brak uprawnienia GPS"); }
    }
    @Override public void onLocationChanged(Location l){
        if(tripOn && lastLocation!=null) tripMeters += lastLocation.distanceTo(l);
        lastLocation=l;
        position.setText(String.format(Locale.US,"Pozycja: %.5f, %.5f",l.getLatitude(),l.getLongitude()));
        speed.setText(String.format(Locale.US,"Prędkość: %.1f kn",l.hasSpeed()?l.getSpeed()*1.94384:0));
        course.setText(String.format(Locale.US,"Kurs: %.0f°",l.hasBearing()?l.getBearing():0));
        if(tripOn) updateTrip();
        if(anchorOn && anchorLocation!=null){
            float d=anchorLocation.distanceTo(l);
            anchor.setText(String.format(Locale.US,"ALARM KOTWICZNY — %.0f m / 40 m",d));
            if(d>40) fireAnchorAlarm(d);
        }
    }
    void updateTrip(){
        long secs=tripStart==0?0:(System.currentTimeMillis()-tripStart)/1000;
        trip.setText(String.format(Locale.US,"Dziennik: %.2f Mm • %02d:%02d:%02d",tripMeters/1852.0,secs/3600,(secs%3600)/60,secs%60));
    }
    void fireAnchorAlarm(float d){
        anchorOn=false;
        Vibrator vib=(Vibrator)getSystemService(VIBRATOR_SERVICE);
        if(Build.VERSION.SDK_INT>=26) vib.vibrate(VibrationEffect.createWaveform(new long[]{0,700,300,700,300,1200},-1)); else vib.vibrate(2500);
        new AlertDialog.Builder(this).setTitle("ALARM KOTWICZNY").setMessage("Jacht oddalił się "+Math.round(d)+" m od pozycji kotwiczenia.").setPositiveButton("OK",null).show();
    }
    void call984(){
        Intent dial=new Intent(Intent.ACTION_DIAL, Uri.parse("tel:984"));
        startActivity(dial);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ_LOC && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) startGps();
        else if(r==REQ_LOC) status.setText("GPS: odmówiono dostępu do lokalizacji");
    }
    @Override public void onProviderEnabled(String p){ startGps(); }
    @Override public void onProviderDisabled(String p){ status.setText("GPS wyłączony"); }
    @Override public void onStatusChanged(String p,int s,Bundle b){}
}