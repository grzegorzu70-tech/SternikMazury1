package pl.sternikmazury.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.media.*;
import android.os.*;

public class AnchorService extends Service implements LocationListener {
    private static final String CHANNEL="anchor_monitor";
    private LocationManager gps;
    private double anchorLat,anchorLon,radius;
    private boolean alarm=false;
    private long lastFix=0;
    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable watchdog=new Runnable(){public void run(){
        if(lastFix>0 && System.currentTimeMillis()-lastFix>30000) trigger("Utracono sygnał GPS");
        handler.postDelayed(this,5000);
    }};
    @Override public void onCreate(){super.onCreate();
        NotificationChannel ch=new NotificationChannel(CHANNEL,"Alarm kotwiczny",NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Monitorowanie kotwicy i ostrzeżenia");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        gps=(LocationManager)getSystemService(LOCATION_SERVICE);
        vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
    }
    private Notification notice(String title,String detail,boolean urgent){
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(detail).setContentIntent(pi).setOngoing(true)
            .setCategory(urgent?Notification.CATEGORY_ALARM:Notification.CATEGORY_SERVICE).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_STICKY;
        String action=intent.getAction();
        if("STOP".equals(action)){stopSelf();return START_NOT_STICKY;}
        if("ACK".equals(action)){silence();return START_STICKY;}
        anchorLat=intent.getDoubleExtra("lat",0);
        anchorLon=intent.getDoubleExtra("lon",0);
        radius=intent.getDoubleExtra("radius",40);
        alarm=false;
        lastFix=System.currentTimeMillis();
        startForeground(1234,notice("Alarm kotwiczny aktywny","Promień: "+Math.round(radius)+" m",false));
        try {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,0,this,Looper.getMainLooper());
        }catch(Exception e){trigger("Brak dostępu do GPS");}
        handler.removeCallbacks(watchdog);handler.postDelayed(watchdog,5000);
        return START_STICKY;
    }
    @Override public void onLocationChanged(Location loc){
        lastFix=System.currentTimeMillis();
        float[] d=new float[1];Location.distanceBetween(anchorLat,anchorLon,loc.getLatitude(),loc.getLongitude(),d);
        Intent update=new Intent("pl.sternikmazury.ANCHOR_UPDATE").setPackage(getPackageName());
        update.putExtra("distance",(double)d[0]);update.putExtra("radius",radius);
        sendBroadcast(update);
        if(d[0]>radius)trigger("Jacht oddalił się o "+Math.round(d[0])+" m (limit "+Math.round(radius)+" m)");
    }
    @Override public void onProviderDisabled(String provider){trigger("Sygnał GPS wyłączony");}
    private void trigger(String message){
        if(alarm)return;
        alarm=true;
        getSystemService(NotificationManager.class).notify(1234,notice("ALARM KOTWICZNY!",message,true));
        Intent i=new Intent("pl.sternikmazury.ANCHOR_ALERT").setPackage(getPackageName());
        i.putExtra("message",message);sendBroadcast(i);
        try {
            Uri tone=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            player=MediaPlayer.create(this,tone);
            if(player!=null){player.setLooping(true);player.start();}
            if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,800,400},0));
        }catch(Exception ignored){}
    }
    private void silence(){
        alarm=false;
        if(player!=null){player.stop();player.release();player=null;}
        if(vibrator!=null)vibrator.cancel();
        getSystemService(NotificationManager.class).notify(1234,notice("Alarm kotwiczny aktywny","Monitorowanie trwa",false));
    }
    @Override public void onDestroy(){
        handler.removeCallbacks(watchdog);
        if(gps!=null)gps.removeUpdates(this);
        silence();super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}