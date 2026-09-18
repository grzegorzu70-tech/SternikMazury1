package pl.sternikmazury.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements LocationListener {
    FrameLayout content;
    TextView headerStatus, gpsPosition, tripLive, anchorLive;
    LinearLayout gpsSpeed, gpsCourse;
    LocationManager locationManager;
    Location lastLocation, anchorLocation;
    boolean tripRunning=false, anchorRunning=false;
    float tripMeters=0, anchorRadius=40;
    long tripStarted=0;
    String selectedBoat="Sukcesja — Maxus 26";
    boolean atHome=true;
    final Handler timer=new Handler(Looper.getMainLooper());
    static final int REQ_LOCATION=10;
    final int NAVY=Color.rgb(6,59,76), SEA=Color.rgb(8,127,140), TURQ=Color.rgb(17,167,160);
    final int BG=Color.rgb(239,248,249), INK=Color.rgb(18,52,59), MUTED=Color.rgb(96,125,130);
    final int DANGER=Color.rgb(198,40,40), WARNING=Color.rgb(245,158,11);

    @Override public void onCreate(Bundle state){
        super.onCreate(state); setContentView(R.layout.activity_main);
        content=findViewById(R.id.content); headerStatus=findViewById(R.id.headerStatus);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        styleNav(R.id.navHome); styleNav(R.id.navWeather); styleNav(R.id.navTrip); styleNav(R.id.navSafety); styleNav(R.id.navTools);
        findViewById(R.id.navHome).setOnClickListener(v->showHome());
        findViewById(R.id.navWeather).setOnClickListener(v->showWeather());
        findViewById(R.id.navTrip).setOnClickListener(v->showTrip());
        findViewById(R.id.navSafety).setOnClickListener(v->showSafety());
        findViewById(R.id.navTools).setOnClickListener(v->showTools());
        timer.post(new Runnable(){ public void run(){ if(tripRunning) refreshTrip(); timer.postDelayed(this,1000); }});
        requestLocation(); showHome();
    }

    ScrollView page(String title, String subtitle){
        ScrollView s=new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(BG);
        LinearLayout root=column(); root.setPadding(dp(16),dp(18),dp(16),dp(28)); s.addView(root);
        root.addView(label(title,26,INK,true));
        TextView sub=label(subtitle,14,MUTED,false); sub.setPadding(0,dp(2),0,dp(14)); root.addView(sub);
        if(!title.equals("Kokpit")){
            Button back=outline("←  WRÓĆ DO MENU");
            back.setOnClickListener(v->showHome());
            root.addView(back);
        }
        s.setTag(root); return s;
    }
    LinearLayout root(ScrollView s){ return (LinearLayout)s.getTag(); }

    void showHome(){
        atHome=true;
        ScrollView s=page("Kokpit","Najważniejsze dane w jednym miejscu");
        LinearLayout r=root(s);
        LinearLayout gps=card(); gps.addView(label("GPS • NA ŻYWO",13,SEA,true));
        LinearLayout numbers=row();
        gpsSpeed=metric("0.0","węzłów"); gpsCourse=metric("—","kurs");
        numbers.addView(gpsSpeed); numbers.addView(gpsCourse); gps.addView(numbers);
        gpsPosition=label("Pozycja: oczekiwanie na GPS…",15,MUTED,false); gps.addView(gpsPosition); r.addView(gps);

        Spinner boat=new Spinner(this); String[] boats={"Sukcesja — Maxus 26","Fanaberia — Maxus 26"};
        boat.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,boats));
        boat.setSelection(selectedBoat.startsWith("Fanaberia")?1:0);
        boat.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){selectedBoat=boats[pos];}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        LinearLayout bc=card(); bc.addView(label("AKTYWNY JACHT",13,SEA,true)); bc.addView(boat); r.addView(bc);

        LinearLayout q=row();
        q.addView(tile("☁","Czy wypływać?","Oceń wiatr i warunki",TURQ,v->showWeather()));
        q.addView(tile("⚓","Alarm kotwiczny","Ustaw własny promień",SEA,v->showAnchor()));
        r.addView(q);
        LinearLayout q2=row();
        q2.addView(tile("▶","Dziennik rejsu","Czas, dystans, historia",NAVY,v->showTrip()));
        q2.addView(tile("✚","SOS 984","Pozycja i szybki telefon",DANGER,v->showSafety()));
        r.addView(q2);
        TextView note=label("Wersja 2.0 • podstawowe funkcje działają bez internetu",13,MUTED,false);
        note.setPadding(dp(4),dp(14),0,0); r.addView(note); setPage(s); updateGpsTexts();
    }

    void showWeather(){
        atHome=false;
        ScrollView s=page("Pogoda i wiatr","Offline: kalkulator Beauforta i decyzja sternika");
        LinearLayout r=root(s), c=card();
        c.addView(label("Prędkość wiatru",17,INK,true));
        EditText wind=input("Wpisz wiatr w km/h"); wind.setInputType(2|8192); c.addView(wind);
        Spinner unit=new Spinner(this); unit.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"km/h","węzły","m/s"})); c.addView(unit);
        TextView result=label("Wpisz wartość, aby ocenić warunki.",18,INK,true); result.setPadding(0,dp(14),0,dp(8)); c.addView(result);
        Button check=action("OCEŃ WARUNKI",TURQ); c.addView(check);
        check.setOnClickListener(v->{
            double val=number(wind); if(val<0){toast("Wpisz prędkość wiatru.");return;}
            double knots=unit.getSelectedItemPosition()==0?val/1.852:unit.getSelectedItemPosition()==2?val*1.94384:val;
            int b=beaufort(knots); String advice=b<=3?"DOBRE WARUNKI — zachowaj zwykłą ostrożność":b<=5?"UWAGA — rozważ refowanie i krótszą trasę":"NIE WYPŁYWAJ — poszukaj bezpiecznego portu";
            result.setText(String.format(Locale.US,"%d°B • %.1f kn\n%s",b,knots,advice)); result.setTextColor(b<=3?SEA:b<=5?WARNING:DANGER);
        });
        r.addView(c);
        LinearLayout guide=card(); guide.addView(label("Szybka skala",17,INK,true));
        guide.addView(label("0–3°B  spokojnie lub umiarkowanie\n4–5°B  silniej — refy i większa ostrożność\n6°B+  nie wypływaj / szukaj schronienia",16,MUTED,false));
        r.addView(guide); setPage(s);
    }

    void showTrip(){
        atHome=false;
        ScrollView s=page("Dziennik rejsu",selectedBoat);
        LinearLayout r=root(s), c=card();
        tripLive=label("Rejs nieaktywny",24,INK,true); c.addView(tripLive);
        Button toggle=action(tripRunning?"ZAKOŃCZ I ZAPISZ REJS":"ROZPOCZNIJ REJS",tripRunning?DANGER:TURQ); c.addView(toggle);
        toggle.setOnClickListener(v->{
            if(!tripRunning){tripRunning=true;tripStarted=System.currentTimeMillis();tripMeters=0;toast("Rejs rozpoczęty: "+selectedBoat);}
            else {tripRunning=false; saveLastTrip(); toast("Rejs zapisany.");}
            showTrip();
        }); r.addView(c);
        LinearLayout history=card(); history.addView(label("OSTATNI ZAPIS",13,SEA,true));
        history.addView(label(getPreferences(0).getString("lastTrip","Brak zapisanych rejsów."),16,INK,false)); r.addView(history);
        refreshTrip(); setPage(s);
    }

    void showAnchor(){
        atHome=false;
        ScrollView s=page("Alarm kotwiczny","Monitoring oddalenia od miejsca kotwiczenia");
        LinearLayout r=root(s), c=card();
        c.addView(label("Promień alarmu w metrach",17,INK,true));
        EditText radius=input("np. 40"); radius.setInputType(2|8192); radius.setText(String.valueOf((int)anchorRadius)); c.addView(radius);
        anchorLive=label(anchorRunning?"Alarm aktywny":"Alarm wyłączony",21,anchorRunning?SEA:MUTED,true); c.addView(anchorLive);
        Button set=action(anchorRunning?"WYŁĄCZ ALARM":"USTAW KOTWICĘ TUTAJ",anchorRunning?DANGER:TURQ); c.addView(set);
        set.setOnClickListener(v->{
            if(anchorRunning){anchorRunning=false;anchorLocation=null;toast("Alarm wyłączony.");}
            else {if(lastLocation==null){toast("Poczekaj na pozycję GPS.");return;} double x=number(radius); if(x<10){toast("Ustaw promień co najmniej 10 m.");return;} anchorRadius=(float)x;anchorLocation=new Location(lastLocation);anchorRunning=true;toast("Kotwica ustawiona.");}
            showAnchor();
        }); r.addView(c);
        r.addView(infoCard("Jak to działa","Telefon kontroluje odległość od zapisanej pozycji. Po przekroczeniu promienia uruchomi wibrację i głośny komunikat. Nie zastępuje wachty kotwicznej."));
        setPage(s);
    }

    void showSafety(){
        atHome=false;
        ScrollView s=page("Bezpieczeństwo i SOS","Pozycja, numer ratunkowy i procedury");
        LinearLayout r=root(s), sos=card();
        TextView coords=label(currentCoords(),20,INK,true); sos.addView(coords);
        Button copy=action("KOPIUJ POZYCJĘ",SEA); copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Pozycja GPS",currentCoords()));toast("Pozycja skopiowana.");}); sos.addView(copy);
        Button call=action("SOS — ZADZWOŃ 984",DANGER); call.setTextSize(20); call.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:984")))); sos.addView(call); r.addView(sos);
        r.addView(infoCard("Człowiek za burtą — MOB","1. Krzyknij „człowiek za burtą”.\n2. Rzuć środki ratunkowe.\n3. Nie trać kontaktu wzrokowego.\n4. Uruchom manewr MOB.\n5. Wezwij pomoc, jeśli sytuacja tego wymaga."));
        r.addView(infoCard("Nagłe załamanie pogody","Załóż kamizelki, zrefuj lub zrzuć żagle, uruchom silnik jeśli bezpieczne, kieruj się do najbliższego osłoniętego brzegu albo portu."));
        setPage(s);
    }

    void showTools(){
        atHome=false;
        ScrollView s=page("Więcej narzędzi","Checklisty, kalkulator i instrukcje");
        LinearLayout r=root(s);
        LinearLayout checks=card(); checks.addView(label("CHECKLISTA PRZED WYPŁYNIĘCIEM",15,INK,true));
        String[] items={"Kamizelki i środki ratunkowe","Silnik oraz paliwo / akumulator","Olinowanie, fały i refowanie","Toaleta, zbiornik i zawory","Pogoda i ostrzeżenia","Telefon naładowany i numer 984"};
        for(String item:items){CheckBox b=new CheckBox(this);b.setText(item);b.setTextSize(16);b.setPadding(0,dp(5),0,dp(5));checks.addView(b);} r.addView(checks);
        LinearLayout calc=card(); calc.addView(label("KALKULATOR ETA",17,INK,true));
        EditText dist=input("Dystans w milach morskich"); dist.setInputType(2|8192); calc.addView(dist);
        EditText knots=input("Prędkość w węzłach"); knots.setInputType(2|8192); calc.addView(knots);
        TextView eta=label("Czas dopłynięcia: —",18,INK,true); calc.addView(eta);
        Button count=action("OBLICZ",SEA); count.setOnClickListener(v->{double d=number(dist),k=number(knots);if(d<0||k<=0){toast("Wpisz poprawne wartości.");return;}double h=d/k;eta.setText(String.format(Locale.US,"Czas dopłynięcia: %d h %02d min",(int)h,(int)((h-(int)h)*60)));});calc.addView(count); r.addView(calc);
        LinearLayout maneuvers=card(); maneuvers.addView(label("ASYSTENT MANEWRÓW",17,INK,true));
        String[][] m={{"Podejście do kei","Sprawdź wiatr, przygotuj cumy i odbijacze, wyznacz załogę, podejdź minimalną bezpieczną prędkością."},{"Odejście od kei","Uruchom silnik, sprawdź bieg jałowy, zdejmuj cumy w ustalonej kolejności i kontroluj dziób oraz rufę."},{"Refowanie","Refuj odpowiednio wcześnie. Ustaw jacht bezpiecznie względem wiatru, zmniejsz powierzchnię grota i sprawdź napięcie lin."},{"Położenie masztu","Tylko przy bezpiecznej pogodzie i z przygotowaną załogą. Zabezpiecz olinowanie oraz obszar pracy masztu."}};
        for(String[] x:m){Button b=outline(x[0]);b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(x[0]).setMessage(x[1]).setPositiveButton("ROZUMIEM",null).show());maneuvers.addView(b);} r.addView(maneuvers);
        setPage(s);
    }

    void requestLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;} startGps();
    }
    void startGps(){try{if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){headerStatus.setText("GPS wyłączony — dotknij, aby włączyć");headerStatus.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));return;}locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,1,this);headerStatus.setText("Asystent żeglarza • GPS aktywny");}catch(SecurityException e){headerStatus.setText("Brak zgody na GPS");}}
    @Override public void onLocationChanged(Location l){if(tripRunning&&lastLocation!=null&&l.getAccuracy()<50)tripMeters+=lastLocation.distanceTo(l);lastLocation=l;updateGpsTexts();if(anchorRunning&&anchorLocation!=null){float d=anchorLocation.distanceTo(l);if(anchorLive!=null)anchorLive.setText(String.format(Locale.US,"Od kotwicy: %.0f m / %.0f m",d,anchorRadius));if(d>anchorRadius)fireAnchorAlarm(d);}}
    void updateGpsTexts(){if(lastLocation==null)return;if(gpsPosition!=null)gpsPosition.setText(currentCoords());if(gpsSpeed!=null)((TextView)((LinearLayout)gpsSpeed).getChildAt(0)).setText(String.format(Locale.US,"%.1f",lastLocation.hasSpeed()?lastLocation.getSpeed()*1.94384:0));if(gpsCourse!=null)((TextView)((LinearLayout)gpsCourse).getChildAt(0)).setText(lastLocation.hasBearing()?String.format(Locale.US,"%.0f°",lastLocation.getBearing()):"—");}
    void fireAnchorAlarm(float d){anchorRunning=false;Vibrator vib=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(Build.VERSION.SDK_INT>=26)vib.vibrate(VibrationEffect.createWaveform(new long[]{0,700,250,700,250,1200},-1));else vib.vibrate(2500);new AlertDialog.Builder(this).setTitle("ALARM KOTWICZNY").setMessage("Jacht oddalił się "+Math.round(d)+" m od kotwicy.").setPositiveButton("WYŁĄCZ",null).show();}
    void refreshTrip(){if(tripLive==null)return;if(!tripRunning){tripLive.setText("Rejs nieaktywny");return;}long sec=(System.currentTimeMillis()-tripStarted)/1000;tripLive.setText(String.format(Locale.US,"%.2f Mm\n%02d:%02d:%02d",tripMeters/1852.0,sec/3600,(sec%3600)/60,sec%60));}
    void saveLastTrip(){long sec=(System.currentTimeMillis()-tripStarted)/1000;String date=new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date());String text=String.format(Locale.US,"%s\n%s • %.2f Mm • %02d:%02d",date,selectedBoat,tripMeters/1852.0,sec/3600,(sec%3600)/60);getPreferences(0).edit().putString("lastTrip",text).apply();}
    String currentCoords(){return lastLocation==null?"Pozycja GPS: jeszcze niedostępna":String.format(Locale.US,"Pozycja: %.5f, %.5f",lastLocation.getLatitude(),lastLocation.getLongitude());}

    void setPage(View v){content.removeAllViews();content.addView(v);}
    LinearLayout column(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return x;}
    LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);x.setGravity(Gravity.CENTER);x.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return x;}
    LinearLayout card(){LinearLayout x=column();x.setPadding(dp(16),dp(16),dp(16),dp(16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(12));x.setLayoutParams(lp);GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(18));g.setStroke(dp(1),Color.rgb(215,232,234));x.setBackground(g);x.setElevation(dp(3));return x;}
    LinearLayout metric(String value,String caption){LinearLayout x=column();x.setGravity(Gravity.CENTER);x.setPadding(dp(4),dp(12),dp(4),dp(12));x.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));x.addView(label(value,34,NAVY,true));x.addView(label(caption,13,MUTED,false));return x;}
    LinearLayout tile(String icon,String title,String sub,int color,View.OnClickListener click){LinearLayout x=column();x.setGravity(Gravity.CENTER);x.setPadding(dp(10),dp(16),dp(10),dp(16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(148),1);lp.setMargins(dp(4),dp(4),dp(4),dp(4));x.setLayoutParams(lp);GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(18));x.setBackground(g);x.setElevation(dp(3));x.addView(label(icon,28,Color.WHITE,true));x.addView(label(title,17,Color.WHITE,true));x.addView(label(sub,12,Color.WHITE,false));x.setOnClickListener(click);return x;}
    LinearLayout infoCard(String title,String body){LinearLayout x=card();x.addView(label(title,18,INK,true));TextView b=label(body,15,MUTED,false);b.setPadding(0,dp(8),0,0);x.addView(b);return x;}
    TextView label(String text,int size,int color,boolean bold){TextView x=new TextView(this);x.setText(text);x.setTextSize(size);x.setTextColor(color);if(bold)x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);x.setLineSpacing(0,1.1f);return x;}
    EditText input(String hint){EditText x=new EditText(this);x.setHint(hint);x.setTextSize(17);x.setSingleLine(true);x.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(8),0,dp(8));x.setLayoutParams(lp);return x;}
    Button action(String text,int color){Button b=new Button(this);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(14));b.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(10),0,0);b.setLayoutParams(lp);return b;}
    Button outline(String text){Button b=new Button(this);b.setText(text);b.setTextColor(NAVY);b.setTextSize(15);GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setStroke(dp(1),TURQ);g.setCornerRadius(dp(12));b.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(7),0,0);b.setLayoutParams(lp);return b;}
    double number(EditText e){try{return Double.parseDouble(e.getText().toString().replace(',','.'));}catch(Exception x){return -1;}}
    int beaufort(double k){double[] t={1,4,7,11,17,22,28,34,41,48,56,64};for(int i=0;i<t.length;i++)if(k<t[i])return i;return 12;}
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    void styleNav(int id){Button b=findViewById(id);b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(0,0,0,0);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_LOCATION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startGps();else headerStatus.setText("GPS: brak pozwolenia");}
    @Override public void onBackPressed(){if(!atHome)showHome();else super.onBackPressed();}
    @Override public void onProviderEnabled(String p){startGps();}@Override public void onProviderDisabled(String p){headerStatus.setText("GPS wyłączony");}@Override public void onStatusChanged(String p,int s,Bundle b){}
}
