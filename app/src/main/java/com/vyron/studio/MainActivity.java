package com.vyron.studio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int PICK_EXPORT = 701;
    private static final int PICK_IMPORT = 702;
    private static final int REQ_NOTIFICATIONS = 703;
    private static final int REQ_SHIZUKU = 704;
    private static final String PREFS = "vyron_state";
    private static final String SESSION_ID = "session_user_id";
    private static final String SETUP_DONE = "setup_done";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private DatabaseHelper db;
    private SharedPreferences prefs;
    private long currentUserId = -1;
    private String currentUserName = "";
    private WebView webView;
    private String pendingExportJson;
    private final LocalPreviewServer localServer = new LocalPreviewServer();

    private int white = Color.rgb(230,238,247);
    private int muted = Color.rgb(127,145,165);
    private int bg = Color.rgb(7,10,16);
    private int panel = Color.rgb(11,16,24);
    private int panel2 = Color.rgb(14,21,31);
    private int line = Color.rgb(26,39,55);
    private int cyan = Color.rgb(72,199,255);
    private int green = Color.rgb(75,227,167);
    private int red = Color.rgb(255,100,124);
    private int violet = Color.rgb(154,123,255);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        db = new DatabaseHelper(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try { Shizuku.addRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) {}
        showSplash();
    }

    @Override protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener); } catch (Throwable ignored) {}
        io.shutdownNow();
        localServer.stop();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private final Shizuku.OnRequestPermissionResultListener shizukuListener = (requestCode, grantResult) -> {
        handler.post(() -> toast(grantResult == PackageManager.PERMISSION_GRANTED ? "Shizuku access granted" : "Shizuku access denied"));
    };

    private void showSplash() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(dp(28), dp(28), dp(28), dp(28)); root.setBackgroundColor(bg);

        TextView logo = new TextView(this); logo.setText("V"); logo.setTextSize(72); logo.setGravity(Gravity.CENTER); logo.setTextColor(cyan); logo.setTypeface(Typeface.DEFAULT_BOLD);
        GradientBox.apply(logo, Color.rgb(9,22,33), Color.rgb(30,108,145), 26, 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(132), dp(132)); lp.gravity = Gravity.CENTER; root.addView(logo, lp);

        TextView title = text("VYRON Code", 28, white, true); title.setGravity(Gravity.CENTER); root.addView(title, wrap());
        TextView sub = text("Code. Run. Ship.", 12, muted, false); sub.setGravity(Gravity.CENTER); root.addView(sub, wrap());
        Space sp = new Space(this); root.addView(sp, new LinearLayout.LayoutParams(1, dp(26)));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress(0); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(cyan));
        root.addView(bar, new LinearLayout.LayoutParams(dp(240), dp(5)));
        TextView status = text("Preparing workspace…", 11, muted, false); status.setGravity(Gravity.CENTER); root.addView(status, wrap());
        setContentView(root);

        logo.setScaleX(.78f); logo.setScaleY(.78f); logo.setAlpha(.2f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).start();
        final String[] messages = {"Starting core…","Warming editor…","Loading workspace…","Preparing terminal…","Checking permissions…","Almost ready…"};
        final long start = System.currentTimeMillis();
        Runnable tick = new Runnable() { @Override public void run() {
            long elapsed = System.currentTimeMillis() - start; int p = (int)Math.min(100, elapsed * 100 / 8000L);
            bar.setProgress(p); status.setText(messages[Math.min(messages.length-1, (p*messages.length)/101)]);
            if (elapsed < 8000) handler.postDelayed(this, 60); else afterSplash();
        }}; handler.post(tick);
    }

    private void afterSplash() {
        long id = prefs.getLong(SESSION_ID, -1);
        if (id > 0) {
            String name = db.getUserName(id);
            if (name != null && !name.isEmpty()) { currentUserId = id; currentUserName = name; showIDE(); return; }
            prefs.edit().remove(SESSION_ID).apply();
        }
        if (!prefs.getBoolean(SETUP_DONE, false)) showPermissionGate(); else showAuth();
    }

    private void showPermissionGate() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(bg);
        LinearLayout root = column(); root.setPadding(dp(22), dp(20), dp(22), dp(28)); scroll.addView(root);
        TextView title = text("One-time setup", 28, white, true); root.addView(title);
        root.addView(text("VYRON can work without these permissions, but they unlock the advanced Android workflow you asked for.", 13, muted, false), marginTop(6));
        root.addView(permissionCard("SHIZUKU", "Optional elevated shell / Android system commands", "Grant Access", cyan, v -> requestShizuku()));
        root.addView(permissionCard("NOTIFICATIONS", "Needed for foreground/background runtime status on Android 13+", "Allow", violet, v -> requestNotifications()));
        root.addView(permissionCard("BACKGROUND", "Open Android battery settings so VYRON can keep its runtime service alive", "Open Settings", green, v -> openBatterySettings()));
        root.addView(text("Android decides whether background work is allowed. Some manufacturers add extra battery restrictions.", 11, Color.rgb(96,113,132), false), marginTop(4));
        Space sp = new Space(this); root.addView(sp, new LinearLayout.LayoutParams(1, 1, 1));
        Button cont = button("Continue to account", cyan); cont.setOnClickListener(v -> { prefs.edit().putBoolean(SETUP_DONE, true).apply(); showAuth(); }); root.addView(cont, fullWidth());
        setContentView(scroll);
    }

    private View permissionCard(String title, String desc, String action, int accent, View.OnClickListener l) {
        LinearLayout card = column(); card.setPadding(dp(14), dp(14), dp(14), dp(12));
        GradientBox.apply(card, panel, line, 18, 1);
        TextView head = text(title, 10, accent, true); card.addView(head);
        card.addView(text(desc, 12, white, false), marginTop(5));
        Button b = button(action, accent); b.setOnClickListener(l); card.addView(b, wrapRight(8));
        return addMargin(card, 0, 12, 0, 0);
    }

    private void showAuth() {
        LinearLayout root = column(); root.setGravity(Gravity.CENTER); root.setPadding(dp(22), dp(28), dp(22), dp(28)); root.setBackgroundColor(bg);
        LinearLayout card = column(); card.setPadding(dp(20), dp(22), dp(20), dp(20)); GradientBox.apply(card, panel, line, 22, 1); root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        TextView brand = text("VYRON", 22, white, true); brand.setGravity(Gravity.CENTER); card.addView(brand);
        TextView sub = text("Your private Android code workspace", 12, muted, false); sub.setGravity(Gravity.CENTER); card.addView(sub, marginTop(4));

        LinearLayout tabs = new LinearLayout(this); tabs.setPadding(dp(4), dp(4), dp(4), dp(4)); GradientBox.apply(tabs, panel2, line, 12, 1); card.addView(tabs, marginTop(20));
        Button createTab = tabButton("Create account"); Button loginTab = tabButton("Login"); tabs.addView(createTab, new LinearLayout.LayoutParams(0, dp(38), 1)); tabs.addView(loginTab, new LinearLayout.LayoutParams(0, dp(38), 1));

        LinearLayout form = column(); card.addView(form, marginTop(12));
        TextView mode = text("CREATE ACCOUNT", 10, cyan, true); form.addView(mode);
        EditText name = field("Name"); form.addView(name, marginTop(8));
        EditText pass = field("Password"); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); form.addView(pass, marginTop(8));
        EditText pass2 = field("Confirm password"); pass2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); form.addView(pass2, marginTop(8));
        TextView message = text("Your password is stored locally as a salted password hash. No Firebase.", 10, muted, false); form.addView(message, marginTop(8));
        Button submit = button("Create", cyan); form.addView(submit, marginTop(14));

        final boolean[] create = {true};
        Runnable style = () -> {
            createTab.setTextColor(create[0] ? white : muted); loginTab.setTextColor(create[0] ? muted : white);
            mode.setText(create[0] ? "CREATE ACCOUNT" : "LOGIN"); submit.setText(create[0] ? "Create" : "Login"); pass2.setVisibility(create[0] ? View.VISIBLE : View.GONE);
            message.setText(create[0] ? "Your password is stored locally as a salted password hash. No Firebase." : "Use the same local account on this device.");
        }; style.run();
        createTab.setOnClickListener(v -> { create[0]=true; clearFieldError(name); clearFieldError(pass); clearFieldError(pass2); style.run(); });
        loginTab.setOnClickListener(v -> { create[0]=false; clearFieldError(name); clearFieldError(pass); clearFieldError(pass2); style.run(); });
        submit.setOnClickListener(v -> {
            String n=name.getText().toString().trim(), p=pass.getText().toString(), p2=pass2.getText().toString();
            if (n.length()<3) { authError(card, name, "Name must be at least 3 characters"); return; }
            if (p.length()<4) { authError(card, pass, "Password must be at least 4 characters"); return; }
            if (create[0] && !p.equals(p2)) { authError(card, pass2, "Passwords do not match"); return; }
            io.execute(() -> {
                try {
                    if (create[0]) {
                        long id = db.createUser(n,p);
                        if (id == -2) { handler.post(() -> authError(card,name,"That account name already exists")); return; }
                        if (id <= 0) throw new Exception("Could not create account");
                        handler.post(() -> loginSuccess(id,n));
                    } else {
                        long id = db.verifyUser(n,p);
                        if (id <= 0) { handler.post(() -> { authError(card,name,"Name or password is incorrect"); authError(card,pass,""); }); return; }
                        handler.post(() -> loginSuccess(id,n));
                    }
                } catch (Exception e) { handler.post(() -> authError(card,pass,"Account operation failed: "+e.getMessage())); }
            });
        });
        setContentView(root);
        root.setAlpha(0f); root.animate().alpha(1f).setDuration(260).start();
    }

    private void loginSuccess(long id, String name) {
        currentUserId=id; currentUserName=name; prefs.edit().putLong(SESSION_ID,id).apply(); showIDE();
    }

    private void authError(View card, EditText field, String message) {
        if (field != null) { GradientBox.apply(field, Color.rgb(45,15,23), Color.rgb(116,39,56), 11, 1); }
        if (message != null && !message.isEmpty()) toast(message);
        TranslateAnimation a = new TranslateAnimation(-10,10,0,0); a.setDuration(45); a.setRepeatMode(Animation.REVERSE); a.setRepeatCount(5); card.startAnimation(a);
    }
    private void clearFieldError(EditText f){ if(f!=null) GradientBox.apply(f, Color.rgb(8,14,21), line, 11, 1); }

    private void showIDE() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(bg);
        webView = new WebView(this); webView.setBackgroundColor(bg); root.addView(webView, new FrameLayout.LayoutParams(-1,-1));
        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportZoom(false);
        s.setUserAgentString(s.getUserAgentString()+" VYRON-Code/1.0");
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new NativeBridge(), "Android");
        webView.loadUrl("file:///android_asset/editor.html");
        setContentView(root);
    }

    public final class NativeBridge {
        @JavascriptInterface public String getProject() { String p=db.loadLatestProject(currentUserId); return p != null ? p : defaultProject(); }
        @JavascriptInterface public void saveProject(String json) { saveProjectJson(json); }
        @JavascriptInterface public void exportProject(String json) { pendingExportJson=json; runOnUiThread(() -> startExportPicker(json)); }
        @JavascriptInterface public void importProject() { runOnUiThread(() -> startImportPicker()); }
        @JavascriptInterface public void runCommandAsync(String command, String requestId) { runTerminal(command, requestId); }
        @JavascriptInterface public void requestShizuku() { runOnUiThread(MainActivity.this::requestShizuku); }
        @JavascriptInterface public void requestNotifications() { runOnUiThread(MainActivity.this::requestNotifications); }
        @JavascriptInterface public void openBatterySettings() { runOnUiThread(MainActivity.this::openBatterySettings); }
        @JavascriptInterface public void openSettings() { runOnUiThread(MainActivity.this::showSettingsDialog); }
        @JavascriptInterface public void openAccount() { runOnUiThread(MainActivity.this::showAccountDialog); }
        @JavascriptInterface public String startLocalServer(String json) { try { return localServer.start(json); } catch(Exception e) { return ""; } }
        @JavascriptInterface public void stopLocalServer() { localServer.stop(); }
        @JavascriptInterface public void setBackgroundMode(boolean enabled) { runOnUiThread(() -> setBackgroundModeInternal(enabled)); }
        @JavascriptInterface public String getShizukuStatus() { return shizukuStatus(); }
    }

    private void saveProjectJson(String json) {
        try {
            JSONObject p = new JSONObject(json); String name = p.optString("name","Untitled Project");
            JSONObject files = p.optJSONObject("files"); if(files==null) throw new Exception("Project files are missing");
            db.saveProject(currentUserId,name,p.toString()); syncProjectToDisk(name, files);
        } catch (Exception e) { toast("Save failed: "+e.getMessage()); }
    }

    private String defaultProject() {
        try {
            JSONObject root=new JSONObject(); root.put("name","Starter Project"); JSONObject f=new JSONObject();
            f.put("index.html","<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>VYRON</title></head><body><h1>Hello VYRON</h1></body></html>");
            f.put("styles/style.css","body{font-family:system-ui;padding:40px;background:#071019;color:white}"); f.put("scripts/main.js","console.log('VYRON ready');"); root.put("files",f); return root.toString();
        } catch(Exception e){return "{\"name\":\"Starter Project\",\"files\":{}}";}
    }

    private File userWorkspaceRoot() { File r=new File(getFilesDir(),"workspace/"+currentUserId); if(!r.exists()) r.mkdirs(); return r; }
    private void syncProjectToDisk(String projectName, JSONObject files) {
        io.execute(() -> syncProjectToDiskNow(files));
    }
    private void syncProjectToDiskNow(JSONObject files) {
        try { File root=userWorkspaceRoot(); deleteChildren(root); String[] names=JSONObject.getNames(files); if(names!=null){ for(String k:names) writeSafeFile(root,k,files.optString(k,"")); } } catch(Exception ignored) {}
    }
    private void deleteChildren(File root) { File[] fs=root.listFiles(); if(fs==null)return; for(File f:fs){if(f.isDirectory())deleteChildren(f); if(!f.delete()){} } }
    private void writeSafeFile(File root,String rel,String text)throws Exception { File f=safeFile(root,rel); File p=f.getParentFile(); if(p!=null&&!p.exists())p.mkdirs(); try(FileOutputStream out=new FileOutputStream(f)){out.write(text.getBytes(StandardCharsets.UTF_8));} }
    private File safeFile(File root,String rel)throws Exception { String clean=rel.replace('\\','/'); while(clean.startsWith("/"))clean=clean.substring(1); if(clean.contains(".."))throw new SecurityException("Unsafe path"); File f=new File(root,clean); String rc=root.getCanonicalPath(), fc=f.getCanonicalPath(); if(!fc.equals(rc)&&!fc.startsWith(rc+File.separator))throw new SecurityException("Unsafe path"); return f; }

    private void runTerminal(String command,String requestId) {
        final String cmd = command == null ? "" : command.trim();
        io.execute(() -> {
            int code=1; String output="";
            try {
                Process p=null;
                if (shouldUseShizuku(cmd) && hasShizukuPermission()) p=newShizukuProcess(cmd);
                if(p==null){
                    String project=db.loadLatestProject(currentUserId); if(project!=null) { JSONObject o=new JSONObject(project); JSONObject fs=o.optJSONObject("files"); if(fs!=null) syncProjectToDiskNow(fs); }
                    File cwd=userWorkspaceRoot(); p=new ProcessBuilder("sh","-c",cmd).directory(cwd).redirectErrorStream(true).start();
                }
                ByteArrayOutputStream buf=new ByteArrayOutputStream(); try(InputStream in=p.getInputStream()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)buf.write(b,0,n);} p.waitFor(30,TimeUnit.SECONDS); code=p.exitValue(); output=buf.toString(StandardCharsets.UTF_8.name());
            } catch(Exception e){output=e.getMessage()==null?e.toString():e.getMessage(); code=1;}
            String encoded=Base64.encodeToString(output.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP); int finalCode=code; String finalEncoded=encoded;
            handler.post(() -> { if(webView!=null) webView.evaluateJavascript("window.NativeTerminal.onResult("+js(requestId)+","+js(finalEncoded)+","+finalCode+")",null); });
        });
    }

    private boolean shouldUseShizuku(String cmd){String c=cmd.toLowerCase();return c.startsWith("pm ")||c.startsWith("cmd ")||c.startsWith("settings ")||c.startsWith("am ")||c.startsWith("dumpsys ");}
    private boolean hasShizukuPermission(){try{return Shizuku.pingBinder() && Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Throwable e){return false;}}
    private Process newShizukuProcess(String cmd) {
        try { Method m=Shizuku.class.getMethod("newProcess",String[].class,String[].class,String.class); return (Process)m.invoke(null,new Object[]{new String[]{"sh","-c",cmd},null,null}); } catch(Throwable ignored) { return null; }
    }

    private String js(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}

    private void startExportPicker(String json) {
        try { JSONObject p=new JSONObject(json); String base=sanitizeName(p.optString("name","VYRON-Project")); Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/zip"); i.putExtra(Intent.EXTRA_TITLE,base+".zip"); startActivityForResult(i,PICK_EXPORT); }
        catch(Exception e){toast("Export failed: "+e.getMessage());}
    }
    private void startImportPicker(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/zip"); startActivityForResult(i,PICK_IMPORT); }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){ super.onActivityResult(requestCode,resultCode,data); if(resultCode!=RESULT_OK||data==null||data.getData()==null)return; Uri uri=data.getData(); if(requestCode==PICK_EXPORT)io.execute(() -> writeZip(uri,pendingExportJson)); else if(requestCode==PICK_IMPORT)io.execute(() -> readZip(uri)); }

    private void writeZip(Uri uri,String json){ try { JSONObject p=new JSONObject(json); JSONObject files=p.getJSONObject("files"); String[] names=JSONObject.getNames(files); try(OutputStream os=getContentResolver().openOutputStream(uri); ZipOutputStream zos=new ZipOutputStream(os)){ if(names!=null){ for(String name:names){String rel=name.replace('\\','/');ZipEntry e=new ZipEntry(rel);zos.putNextEntry(e);zos.write(files.optString(name,"").getBytes(StandardCharsets.UTF_8));zos.closeEntry();} } } handler.post(()->toast("✓ Exported "+sanitizeName(p.optString("name","Project"))+".zip")); } catch(Exception e){handler.post(()->toast("Export failed: "+e.getMessage()));} }

    private void readZip(Uri uri){ try { Map<String,String> files=new LinkedHashMap<>(); List<String> entries=new ArrayList<>(); try(InputStream in=getContentResolver().openInputStream(uri);ZipInputStream zis=new ZipInputStream(in)){ZipEntry e;while((e=zis.getNextEntry())!=null){if(e.isDirectory())continue;String raw=e.getName().replace('\\','/');if(raw.contains(".."))continue;entries.add(raw);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n,total=0;while((n=zis.read(b))!=-1){total+=n;if(total>5_000_000)throw new Exception("A file inside the ZIP is too large");out.write(b,0,n);}files.put(raw,out.toString(StandardCharsets.UTF_8.name()));}}
            String prefix=commonTopFolder(files.keySet());Map<String,String> normalized=new LinkedHashMap<>();for(Map.Entry<String,String> e:files.entrySet()){String k=prefix.isEmpty()?e.getKey():e.getKey().substring(prefix.length());while(k.startsWith("/"))k=k.substring(1);if(!k.isEmpty())normalized.put(k,e.getValue());}
            JSONObject root=new JSONObject();String fileName=getFileName(uri);String name=fileName.endsWith(".zip")?fileName.substring(0,fileName.length()-4):fileName;root.put("name",name);JSONObject fo=new JSONObject();for(Map.Entry<String,String> e:normalized.entrySet())fo.put(e.getKey(),e.getValue());root.put("files",fo);String result=root.toString();db.saveProject(currentUserId,name,result);syncProjectToDisk(name,fo);handler.post(()->{if(webView!=null)webView.evaluateJavascript("window.onNativeProjectImported("+js(result)+")",null);toast("✓ Imported "+name);});
        } catch(Exception e){handler.post(()->toast("Import failed: "+e.getMessage()));}}

    private String commonTopFolder(Iterable<String> names){String first=null;for(String n:names){String[] p=n.split("/");if(p.length<2)return "";if(first==null)first=p[0];else if(!first.equals(p[0]))return "";}return first==null?"":first+"/";}
    private String getFileName(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null && c.moveToFirst()) return c.getString(0);
        }catch(Exception ignored){}
        String s=uri.getPath(); int slash=s==null?-1:s.lastIndexOf('/'); return slash>=0?s.substring(slash+1):(s==null?"Imported Project.zip":s);
    }
    private String sanitizeName(String s){String x=s.replaceAll("[^A-Za-z0-9._ -]","-").trim();return x.isEmpty()?"VYRON-Project":x;}

    private void showSettingsDialog(){
        LinearLayout box=column();box.setPadding(dp(4),0,dp(4),0);
        TextView info=text("Workspace / Android",18,white,true);box.addView(info);box.addView(text("Account: "+currentUserName,11,muted,false),marginTop(4));
        Button sh=button("Shizuku: "+shizukuStatus(),cyan);sh.setOnClickListener(v->requestShizuku());box.addView(sh,marginTop(12));
        Button notif=button("Notifications: "+(hasNotificationPermission()?"Allowed":"Not allowed"),violet);notif.setOnClickListener(v->requestNotifications());box.addView(notif,marginTop(8));
        Button back=button("Background: "+(isIgnoringBatteryOptimizations()?"Unrestricted":"System controlled"),green);back.setOnClickListener(v->openBatterySettings());box.addView(back,marginTop(8));
        Button keep=button("Toggle background runtime",white);keep.setOnClickListener(v->{setBackgroundModeInternal(true);});box.addView(keep,marginTop(8));
        Button logout=button("Log out",red);logout.setOnClickListener(v->{prefs.edit().remove(SESSION_ID).apply();stopBackgroundService();localServer.stop();if(webView!=null){webView.destroy();webView=null;}showAuth();});box.addView(logout,marginTop(8));
        new AlertDialog.Builder(this).setTitle("VYRON Settings").setView(box).setNegativeButton("Close",null).show();
    }

    private void showAccountDialog(){new AlertDialog.Builder(this).setTitle("VYRON account").setMessage("Signed in as "+currentUserName+"\n\nLocal SQLite account. Passwords are hashed on-device.").setNegativeButton("Close",null).setPositiveButton("Log out",(d,w)->{prefs.edit().remove(SESSION_ID).apply();stopBackgroundService();localServer.stop();showAuth();}).show();}

    private void requestNotifications(){if(Build.VERSION.SDK_INT>=33){if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);else toast("Notifications are already allowed");}else toast("Notifications are controlled by system settings on this Android version");}
    private boolean hasNotificationPermission(){return Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}

    private void openBatterySettings(){try{Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);i.setData(Uri.parse("package:"+getPackageName()));startActivity(i);}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));} }
    private boolean isIgnoringBatteryOptimizations(){PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);return Build.VERSION.SDK_INT<23||p==null||p.isIgnoringBatteryOptimizations(getPackageName());}

    private String shizukuStatus(){try{if(Shizuku.isPreV11())return "unsupported";if(!Shizuku.pingBinder())return "not running";return Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED?"granted":"ready / grant";}catch(Throwable e){return "not available";}}
    private boolean isShizukuInstalled(){PackageManager pm=getPackageManager();try{pm.getPackageInfo("moe.shizuku.privileged.api",0);return true;}catch(Exception e){return false;}}
    private void requestShizuku(){try{if(!isShizukuInstalled()){Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/"));startActivity(i);return;}if(!Shizuku.pingBinder()){Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(launch!=null)startActivity(launch);else toast("Open Shizuku and start its service first");return;}if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){toast("Shizuku access already granted");return;}Shizuku.requestPermission(REQ_SHIZUKU);}catch(Throwable e){toast("Shizuku error: "+e.getMessage());}}

    private void setBackgroundModeInternal(boolean enabled){if(enabled){if(Build.VERSION.SDK_INT>=26)startForegroundService(new Intent(this,KeepAliveService.class));else startService(new Intent(this,KeepAliveService.class));toast("Background runtime enabled");}else stopBackgroundService();}
    private void stopBackgroundService(){try{stopService(new Intent(this,KeepAliveService.class));}catch(Exception ignored){}}

    private static final class GradientBox {
        static void apply(View v,int fill,int stroke,int radius,int sw){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);d.setStroke(sw,stroke);v.setBackground(d);}
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} 
    private TextView text(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;}
    private LinearLayout column(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-2,-2);}
    private LinearLayout.LayoutParams fullWidth(){return new LinearLayout.LayoutParams(-1,dp(48));}
    private LinearLayout.LayoutParams marginTop(int px){LinearLayout.LayoutParams lp=wrap();lp.topMargin=dp(px);return lp;}
    private View addMargin(View v,int l,int t,int r,int b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(l),dp(t),dp(r),dp(b));v.setLayoutParams(lp);return v;}
    private LinearLayout.LayoutParams wrapRight(int top){LinearLayout.LayoutParams lp=wrap();lp.gravity=Gravity.END;lp.topMargin=dp(top);return lp;}
    private Button button(String label,int accent){Button b=new Button(this);b.setText(label);b.setTextColor(white);b.setTextSize(13);b.setAllCaps(false);GradientBox.apply(b,Color.rgb(10,19,29),accent,12,1);return b;}
    private Button tabButton(String label){Button b=button(label, line);b.setTextColor(muted);b.setTextSize(12);return b;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(91,108,126));e.setTextColor(white);e.setTextSize(14);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);GradientBox.apply(e,Color.rgb(8,14,21),line,11,1);return e;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
