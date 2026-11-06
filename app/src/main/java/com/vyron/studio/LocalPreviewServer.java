package com.vyron.studio;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LocalPreviewServer {
    private final Object lock = new Object();
    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptThread;
    private Map<String,String> files = new HashMap<>();

    public String start(String projectJson) throws Exception {
        JSONObject root = new JSONObject(projectJson);
        JSONObject f = root.optJSONObject("files");
        Map<String,String> next = new HashMap<>();
        if (f != null) {
            String[] names = JSONObject.getNames(f);
            if (names != null) for (String name : names) {
                String safe = normalize(name);
                if (!safe.isEmpty()) next.put(safe, f.optString(name, ""));
            }
        }
        if (!next.containsKey("index.html")) {
            for (String k : next.keySet()) if (k.toLowerCase(Locale.US).endsWith("index.html")) { next.put("index.html", next.get(k)); break; }
        }
        synchronized (lock) {
            stopLocked();
            files = next;
            server = new ServerSocket(0, 40, InetAddress.getByName("127.0.0.1"));
            running = true;
            final int port = server.getLocalPort();
            acceptThread = new Thread(() -> acceptLoop(), "vyron-local-http");
            acceptThread.setDaemon(true);
            acceptThread.start();
            return "http://127.0.0.1:" + port + "/";
        }
    }

    public void stop() { synchronized (lock) { stopLocked(); } }

    public boolean isRunning() { return running && server != null && !server.isClosed(); }

    private void stopLocked() {
        running = false;
        if (server != null) try { server.close(); } catch (IOException ignored) {}
        server = null;
        acceptThread = null;
        files = new HashMap<>();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = server.accept();
                Thread t = new Thread(() -> handle(socket), "vyron-http-client");
                t.setDaemon(true); t.start();
            } catch (IOException e) { if (running) { /* server was interrupted; loop will exit if stop was requested */ } }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket; BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII)); OutputStream out = s.getOutputStream()) {
            String request = in.readLine();
            String header;
            while ((header = in.readLine()) != null && !header.isEmpty()) { /* headers ignored */ }
            if (request == null) return;
            String[] parts = request.split("\\s+");
            String path = parts.length > 1 ? parts[1] : "/";
            int q = path.indexOf('?'); if (q >= 0) path = path.substring(0,q);
            path = normalize(URLDecoder.decode(path, "UTF-8"));
            if (path.isEmpty()) path = "index.html";
            String body = files.get(path);
            if (body == null && "index.html".equals(path)) body = files.get("/index.html");
            if (body == null) { write(out,404,"text/plain; charset=utf-8","404 Not Found"); return; }
            write(out,200,mime(path),body);
        } catch (Exception ignored) {}
    }

    private void write(OutputStream out,int code,String type,String body)throws Exception{
        byte[] data=body.getBytes(StandardCharsets.UTF_8);
        String head="HTTP/1.1 "+code+" "+(code==200?"OK":"Not Found")+"\r\n"+
                "Content-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();
    }

    private static String normalize(String p){
        String x=(p==null?"":p).replace('\\','/'); while(x.startsWith("/"))x=x.substring(1); if(x.contains(".."))return ""; return x;
    }
    private static String mime(String path){
        String p=path.toLowerCase(Locale.US); if(p.endsWith(".html")||p.endsWith(".htm"))return "text/html; charset=utf-8"; if(p.endsWith(".css"))return "text/css; charset=utf-8"; if(p.endsWith(".js")||p.endsWith(".mjs"))return "text/javascript; charset=utf-8"; if(p.endsWith(".json"))return "application/json; charset=utf-8"; if(p.endsWith(".svg"))return "image/svg+xml"; if(p.endsWith(".png"))return "image/png"; if(p.endsWith(".jpg")||p.endsWith(".jpeg"))return "image/jpeg"; if(p.endsWith(".gif"))return "image/gif"; if(p.endsWith(".txt")||p.endsWith(".md"))return "text/plain; charset=utf-8"; return "application/octet-stream";
    }
}
