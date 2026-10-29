package com.azad.vpn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AZAD VPN milestone 1: native profile manager and interface.
 * Important: this milestone deliberately does NOT pretend the VPN tunnel is connected.
 * The libbox dependency is present, but command-server/VpnService lifecycle integration
 * must be completed and tested before Connect can be considered functional.
 */
public class MainActivity extends Activity {
    private static final int PICK_CONFIG = 41;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout root, content, profileList;
    private TextView statusText, connectButton, selectedName, selectedInfo, pingText;
    private JSONArray profiles = new JSONArray();
    private int selectedIndex = -1;
    private String language = "fa", theme = "blue";
    private boolean connected = false;

    private static final String[] THEME_NAMES = {"blue", "green", "purple", "gold", "red"};
    private static final int[] ACCENTS = {Color.rgb(55, 202, 225), Color.rgb(47, 220, 145), Color.rgb(178, 111, 255), Color.rgb(226, 184, 91), Color.rgb(255, 91, 112)};
    private int accent() { for (int i=0;i<THEME_NAMES.length;i++) if (THEME_NAMES[i].equals(theme)) return ACCENTS[i]; return ACCENTS[0]; }
    private boolean fa() { return "fa".equals(language); }
    private String t(String faText, String enText) { return fa() ? faText : enText; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("azad_settings", MODE_PRIVATE);
        language = prefs.getString("language", "fa");
        theme = prefs.getString("theme", "blue");
        try { profiles = new JSONArray(prefs.getString("profiles", "[]")); } catch (Exception ignored) { profiles = new JSONArray(); }
        selectedIndex = prefs.getInt("selected", -1);
        if (selectedIndex < 0 || selectedIndex >= profiles.length()) selectedIndex = profiles.length() == 0 ? -1 : 0;
        render();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable bg(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), strokeColor); return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(Color.WHITE); b.setBackground(bg(0xFF17253A, 16, accent(), 1));
        b.setPadding(dp(12), dp(6), dp(12), dp(6)); return b;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    private void add(LinearLayout parent, View v, int width, int height) { parent.addView(v, new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height))); }

    private void render() {
        int accent = accent();
        root = column(); root.setBackgroundColor(0xFF08111F);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = column(); pad(content, 18, 16, 18, 24);
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,-1));

        LinearLayout header = row();
        LinearLayout titleBlock = column();
        TextView title = text("® AZAD VPN", 25, Color.WHITE, true);
        TextView subtitle = text(t("مدیریت سرور و پروفایل", "Server & profile manager"), 13, 0xFF9BAEC7, false);
        add(titleBlock,title,-1,36); add(titleBlock,subtitle,-1,24);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0,-2,1));
        Button settings = button("⚙ " + t("تنظیمات", "Settings")); settings.setOnClickListener(v -> settingsDialog()); add(header,settings,-2,48);
        add(content, header,-1,64);

        LinearLayout panel = column(); panel.setBackground(bg(0xFF101D30, 24, 0xFF20344D, 1)); pad(panel,16,16,16,16);
        statusText = text(connected ? t("متصل", "Connected") : t("آمادهٔ انتخاب سرور", "Ready to select a server"),14,connected?0xFF47E0A4:0xFFB9C8DC,true);
        statusText.setGravity(Gravity.CENTER);
        connectButton = text((connected ? "● " + t("متصل", "Connected") : "⏻  " + t("اتصال", "Connect")) + "   ·   " + currentPingLabel(),20,Color.WHITE,true);
        connectButton.setGravity(Gravity.CENTER); connectButton.setBackground(bg(0xFF162B42, 100, connected ? 0xFF36E59B : accent, 3));
        pad(connectButton,10,24,10,24); connectButton.setOnClickListener(v -> connectAction());
        add(panel,statusText,-1,30); add(panel,connectButton,-1,95);
        selectedName = text("",16,Color.WHITE,true); selectedInfo = text("",13,0xFF9FB1C8,false); pingText = text("",13,accent,false);
        pad(selectedName,0,10,0,0); add(panel,selectedName,-1,30); add(panel,selectedInfo,-1,28); add(panel,pingText,-1,28);
        add(content,panel,-1,-2);

        LinearLayout actions = row(); Button paste = button("＋ " + t("افزودن لینک", "Add link")); Button file = button("▤ " + t("واردکردن فایل", "Import file"));
        paste.setOnClickListener(v -> pasteDialog()); file.setOnClickListener(v -> pickFile());
        actions.addView(paste,new LinearLayout.LayoutParams(0,dp(48),1)); LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(10),1); actions.addView(new View(this),gap); actions.addView(file,new LinearLayout.LayoutParams(0,dp(48),1));
        pad(actions,0,12,0,8); add(content,actions,-1,60);

        TextView section = text(t("سرورهای ذخیره‌شده", "Saved servers") + "  ·  " + profiles.length(),17,Color.WHITE,true); pad(section,2,8,0,8); add(content,section,-1,42);
        profileList = column(); add(content,profileList,-1,-2); rebuildProfiles(); updateSelectedDisplay();
        setContentView(root);
    }
    private String currentPingLabel() {
        JSONObject p = getSelected(); if (p == null) return "— ms";
        String s = p.optString("ping", ""); return s.isEmpty() ? "— ms" : s + " ms";
    }
    private JSONObject getSelected() { if (selectedIndex < 0 || selectedIndex >= profiles.length()) return null; return profiles.optJSONObject(selectedIndex); }
    private void updateSelectedDisplay() {
        JSONObject p = getSelected(); if (selectedName == null) return;
        if (p == null) { selectedName.setText(t("هنوز سروری اضافه نشده", "No server added yet")); selectedInfo.setText(t("لینک یا فایل کانفیگ را وارد کن", "Import a config link or file")); pingText.setText(t("پینگ: نامشخص", "Ping: unknown")); }
        else {
            selectedName.setText(p.optString("name", "Server")); selectedInfo.setText(p.optString("protocol", "Unknown") + "  •  " + p.optString("host", "host unknown") + ":" + p.optString("port", "?"));
            String ping = p.optString("ping", ""); pingText.setText(t("زمان پاسخ سرور: ","Server response: ") + (ping.isEmpty()?t("آزمایش نشده","not tested"):ping+" ms"));
            if (connectButton != null) connectButton.setText((connected ? "● " + t("متصل", "Connected") : "⏻  " + t("اتصال", "Connect")) + "   ·   " + (ping.isEmpty()?"—":ping+" ms"));
        }
    }
    private void rebuildProfiles() {
        if (profileList == null) return; profileList.removeAllViews();
        for (int i=0;i<profiles.length();i++) {
            final int idx=i; JSONObject p=profiles.optJSONObject(i); if(p==null) continue;
            LinearLayout card=row(); card.setBackground(bg(idx==selectedIndex?0xFF172B42:0xFF101D2D,18,idx==selectedIndex?accent():0xFF21334A,idx==selectedIndex?2:1)); pad(card,12,10,10,10);
            LinearLayout info=column(); TextView nm=text(p.optString("name","Server"),15,Color.WHITE,true); TextView meta=text(p.optString("protocol","Unknown")+"  •  "+p.optString("host","?")+":"+p.optString("port","?"),11,0xFF9DAFC6,false); TextView ping=text(p.optString("ping","").isEmpty()?t("پینگ: آزمایش نشده","Ping: not tested"):p.optString("ping")+" ms",12,accent,false);
            add(info,nm,-1,28); add(info,meta,-1,25); add(info,ping,-1,24); card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            Button test=button("↻"); test.setMinWidth(dp(42)); test.setOnClickListener(v->testPing(idx)); add(card,test,-2,42);
            Button choose=button(idx==selectedIndex?t("انتخاب‌شده","Selected"):t("انتخاب","Select")); choose.setOnClickListener(v->{selectedIndex=idx; prefs.edit().putInt("selected",selectedIndex).apply(); render();}); add(card,choose,-2,42);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(9); profileList.addView(card,lp);
        }
    }
    private void pasteDialog() {
        EditText input = new EditText(this); input.setHint("vless://… / vmess://… / https://subscription…"); input.setTextColor(Color.WHITE); input.setHintTextColor(0xFF8A9CB2); input.setMinLines(3); input.setGravity(Gravity.TOP); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setBackground(bg(0xFF101D30,12,0xFF30445F,1)); pad(input,12,12,12,12);
        new AlertDialog.Builder(this).setTitle(t("افزودن کانفیگ یا لینک اشتراک", "Add config or subscription URL")).setView(input).setNegativeButton(t("لغو","Cancel"),null).setPositiveButton(t("بررسی و ذخیره","Parse & save"),(d,w)->importText(input.getText().toString())).show();
    }
    private void pickFile() { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,PICK_CONFIG); }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==PICK_CONFIG&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){ try{String s=readUri(data.getData()); importText(s);}catch(Exception e){toast(t("خواندن فایل ناموفق بود","Could not read file"));} } }
    private String readUri(Uri uri)throws Exception { StringBuilder b=new StringBuilder(); try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){b.append(line).append('\n');if(b.length()>2_000_000)throw new Exception("File too large");}} return b.toString(); }
    private void importText(String raw) {
        String text=raw.trim(); if(text.isEmpty()){toast(t("ورودی خالی است","Input is empty"));return;}
        if(text.startsWith("http://")||text.startsWith("https://")){
            toast(t("در حال دریافت لینک اشتراک…","Fetching subscription…")); worker.execute(()->{try{ URL u=new URL(text); java.net.HttpURLConnection c=(java.net.HttpURLConnection)u.openConnection(); c.setConnectTimeout(7000); c.setReadTimeout(9000); c.setInstanceFollowRedirects(true); c.setRequestProperty("User-Agent","AZAD-VPN/0.1"); StringBuilder b=new StringBuilder(); try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null&&b.length()<2_000_000)b.append(line).append('\n');} c.disconnect(); String body=b.toString().trim(); try{body=new String(Base64.getMimeDecoder().decode(body),StandardCharsets.UTF_8);}catch(Exception ignored){} final String content=body; runOnUiThread(()->parseBatch(content));}catch(Exception e){runOnUiThread(()->toast(t("دریافت اشتراک ناموفق بود","Subscription fetch failed")));}}); return;
        }
        parseBatch(text);
    }
    private void parseBatch(String raw) {
        int added=0; String[] lines=raw.split("\\r?\\n");
        for(String line:lines){String s=line.trim(); if(s.isEmpty()||s.startsWith("#"))continue;
            try { JSONObject p=parseProfile(s); if(p!=null&&!isDuplicate(p.optString("raw"))){profiles.put(p);added++;} }catch(Exception ignored){}
        }
        if(added>0){if(selectedIndex<0)selectedIndex=0;saveProfiles();render();toast(t("تعداد "+added+" کانفیگ اضافه شد","Added "+added+" config(s)"));}
        else if(raw.contains("client\n")||raw.contains("remote ")||raw.contains("dev ")){ // OpenVPN profile can be saved but requires dedicated OpenVPN adapter.
            try{JSONObject p=new JSONObject();p.put("name","OpenVPN profile");p.put("protocol","OpenVPN");p.put("host","file");p.put("port","—");p.put("ping","");p.put("raw",raw); profiles.put(p); if(selectedIndex<0)selectedIndex=0;saveProfiles();render();toast(t("فایل OpenVPN ذخیره شد؛ موتور آن هنوز ادغام نشده","OpenVPN file saved; its engine is not integrated yet"));}catch(Exception ignored){}
        } else toast(t("کانفیگ قابل‌شناسایی پیدا نشد","No recognizable config found"));
    }
    private JSONObject parseProfile(String s)throws Exception {
        String proto="Unknown",host="",port="",name="";
        if(s.startsWith("vless://")||s.startsWith("vmess://")||s.startsWith("trojan://")||s.startsWith("ss://")||s.startsWith("hysteria2://")||s.startsWith("hy2://")||s.startsWith("tuic://")){
            String scheme=s.substring(0,s.indexOf("://")); proto=scheme.toUpperCase(Locale.ROOT);
            if(s.startsWith("vmess://")) { String enc=s.substring(8).split("#",2)[0]; String json=new String(Base64.getMimeDecoder().decode(enc),StandardCharsets.UTF_8); JSONObject j=new JSONObject(json); host=j.optString("add",""); port=String.valueOf(j.opt("port")); name=j.optString("ps",""); }
            else {
                Uri u=Uri.parse(s); host=u.getHost()==null?"":u.getHost(); port=u.getPort()<0?"":String.valueOf(u.getPort());
                String frag=u.getFragment(); if(frag!=null)name=URLDecoder.decode(frag,"UTF-8");
                if(host.isEmpty()&&s.startsWith("ss://")) { String noFrag=s.substring(5).split("#",2)[0]; try{String decoded=new String(Base64.getUrlDecoder().decode(noFrag),StandardCharsets.UTF_8);int at=decoded.lastIndexOf('@'); if(at>=0){String hp=decoded.substring(at+1);int c=hp.lastIndexOf(':');host=c>0?hp.substring(0,c):hp;port=c>0?hp.substring(c+1):"";}}catch(Exception ignored){} }
            }
        } else if(s.startsWith("[Interface]")&&s.contains("[Peer]")){proto="WireGuard";host="config-file";port="—";}
        else return null;
        if(host.isEmpty())host="unknown";if(port.isEmpty())port="?";if(name.isEmpty())name=proto+" · "+host;
        JSONObject p=new JSONObject();p.put("name",name);p.put("protocol",proto);p.put("host",host);p.put("port",port);p.put("ping","");p.put("raw",s); return p;
    }
    private boolean isDuplicate(String raw) { for(int i=0;i<profiles.length();i++){JSONObject p=profiles.optJSONObject(i);if(p!=null&&raw.equals(p.optString("raw")))return true;}return false; }
    private void saveProfiles(){prefs.edit().putString("profiles",profiles.toString()).putInt("selected",selectedIndex).apply();}
    private void testPing(int index) {
        JSONObject p=profiles.optJSONObject(index); if(p==null)return; final String host=p.optString("host",""); final int port; try{port=Integer.parseInt(p.optString("port",""));}catch(Exception e){toast(t("این پروفایل پورت قابل آزمایش ندارد","This profile has no testable port"));return;}
        toast(t("در حال آزمایش پاسخ TCP…","Testing TCP response…")); worker.execute(()->{long start=System.nanoTime();String result="";try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(host,port),3500);result=String.valueOf((System.nanoTime()-start)/1_000_000);}catch(Exception e){result="timeout";}final String r=result;runOnUiThread(()->{JSONObject cur=profiles.optJSONObject(index);if(cur!=null){try{cur.put("ping",r.equals("timeout")?"":r);cur.put("pingStatus",r);}catch(Exception ignored){}saveProfiles();render();toast(r.equals("timeout")?t("پاسخ TCP دریافت نشد؛ این به‌تنهایی ثابت نمی‌کند VPN خراب است","No TCP response; this alone doesn't prove the VPN is unusable"):t("زمان پاسخ TCP: "+r+" ms","TCP response time: "+r+" ms"));}});});
    }
    private void connectAction() {
        if(selectedIndex<0){toast(t("اول یک کانفیگ اضافه و انتخاب کن","Add and select a config first"));return;}
        // Don't fake a successful connection: a fully integrated libbox command server and VpnService are still required.
        new AlertDialog.Builder(this).setTitle(t("موتور اتصال هنوز کامل نشده","VPN engine integration pending"))
                .setMessage(t("این نسخه کانفیگ‌ها را ذخیره و زمان پاسخ TCP را آزمایش می‌کند، اما هنوز تونل VPN واقعی را راه‌اندازی نمی‌کند. تا وقتی موتور sing-box و سرویس Android VpnService کامل و آزمایش نشده‌اند، دکمه سبز یا وضعیت «متصل» نشان داده نمی‌شود.",
                        "This build stores profiles and tests TCP response time, but it does not start a real VPN tunnel yet. It will not show green/Connected until sing-box and Android VpnService are fully integrated and tested."))
                .setPositiveButton(t("متوجه شدم","OK"),null).show();
    }
    private void settingsDialog() {
        String[] langs={"فارسی","English"}; new AlertDialog.Builder(this).setTitle(t("زبان برنامه","App language")).setSingleChoiceItems(langs,fa()?0:1,(d,which)->{language=which==0?"fa":"en";prefs.edit().putString("language",language).apply();d.dismiss();render();settingsDialogTheme();}).setNegativeButton(t("بستن","Close"),null).setNeutralButton(t("انتخاب تم","Choose theme"),(d,w)->settingsDialogTheme()).show();
    }
    private void settingsDialogTheme() { String[] names={t("آبی اقیانوسی","Ocean blue"),t("سبز نئونی","Neon green"),t("بنفش","Purple"),t("مشکی و طلایی","Black & gold"),t("قرمز","Red")}; new AlertDialog.Builder(this).setTitle(t("رنگ برنامه","App theme")).setSingleChoiceItems(names,themeIndex(),(d,w)->{theme=THEME_NAMES[w];prefs.edit().putString("theme",theme).apply();d.dismiss();render();}).setNegativeButton(t("بستن","Close"),null).show(); }
    private int themeIndex(){for(int i=0;i<THEME_NAMES.length;i++)if(THEME_NAMES[i].equals(theme))return i;return 0;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
