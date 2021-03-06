package com.mnemosyne.webviewtest;

import static java.lang.Thread.sleep;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.webkit.JavascriptInterface;

public class SearchActivity extends AppCompatActivity {
    WebView wView;
    Queue<String> queue = new LinkedList<>();
    String postURL;
    String postParam;
    String searchUrl = "";
    String searchScript = "(() => {})();";
    Hashtable<String, Hashtable<String, String>> htLogin;
    SQLiteDatabase sqlite;
    SharedPreferences spf;
    MqttAndroidClient mqtt;
    String urlMqtt = "tcp://dev.mnemosyne.co.kr:1883";
    String urlHeader = "http://mnemosynesolutions.co.kr:8080/";
    // String urlHeader = "http://10.0.2.2:8080/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        // ?????? ????????? ??????
        setTitle("teezzim search by FCM");

        // preference
        spf = getSharedPreferences("DEVICE", MODE_PRIVATE);

        // mqtt
        mqtt = new MqttAndroidClient(this, urlMqtt, MqttClient.generateClientId());
        setMqtt();

        // ????????? ????????? ??????
        String strAccountResult = getPostCall(urlHeader + "account", "{}");
        setLoginAdminAccount(strAccountResult);

        // ?????????????????? ?????? ??????
        Intent service = getIntent();
        String clubEngName = service.getStringExtra("club");
        String clubId = service.getStringExtra("club_id");

        String param = "{\"club\": \"" + clubEngName + "\"}";
        String strResult = getPostCall(urlHeader + "searchbot", param);
        Log.d("script", strResult);
        // json parse
        JSONObject json;
        String scriptTemplate;
        try {
            json = new JSONObject(strResult);
            scriptTemplate = json.getString("script");
            searchUrl = json.getString("url");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        // params into template script
        String deviceId = spf.getString("UUID", "");
        String deviceToken = spf.getString("token", "");
        Hashtable<String, String> params = new Hashtable<String, String>();
        params.put("deviceId", deviceId);
        params.put("deviceToken", deviceToken);
        params.put("golfClubId", clubId);
        Log.d("extra", clubEngName + "::" + clubId);

        searchScript = setStringTemplate(params, scriptTemplate);

        // ??????
        wView = (WebView) findViewById(R.id.wView);
        WebSettings ws = wView.getSettings();
        ws.setJavaScriptEnabled(true);

        WebViewClient wvc = getSearchWebviewClient(searchScript);
        wView.setWebViewClient(wvc);
        wView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                try{
                    Log.d("mqtt", "mqtt webview log!!" + message.message());
                    mqtt.publish("TZLOG", message.message().getBytes(StandardCharsets.UTF_8), 0, false );
                    Log.d("mqtt", "mqtt webview log end!!" + message.message());
                } catch(MqttException e) {
                    e.printStackTrace();
                }
                return super.onConsoleMessage(message);
            }
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                result.confirm();
                return true;
            }
        });
        wView.loadUrl(searchUrl);

        AndroidController ac = new AndroidController();
        wView.addJavascriptInterface(ac, "AndroidController");

    }
    public void setMqtt() {
        IMqttToken token = null;
        try{
            MqttConnectOptions mcops = new MqttConnectOptions();
            mcops.setCleanSession(false);
            mcops.setAutomaticReconnect(true);
            mcops.setWill("aaa", "i am going offline".getBytes(StandardCharsets.UTF_8), 1, true);

            token = mqtt.connect(mcops);
        } catch (MqttException e) {
            e.printStackTrace();
            return;
        }
        token.setActionCallback(new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                disconnectedBufferOptions.setBufferEnabled(true);
                disconnectedBufferOptions.setBufferSize(100);
                disconnectedBufferOptions.setPersistBuffer(true);
                disconnectedBufferOptions.setDeleteOldestMessages(false);
                mqtt.setBufferOpts(disconnectedBufferOptions);
                Log.d("mqtt", "mqtt connection success!!");
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e("mqtt", "Failure " + exception.toString());
            }
        });
    };
    public class AndroidController {
        final public Handler handler = new Handler();
        @JavascriptInterface
        public void message(final String message) {
            Log.d("message from webview", message);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    // wView.loadUrl("javascript:(() => { console.log('end'); })();");
                    if(message.equals("end of procGolfSchedule!")) finish();
                }
            });
        };
    };
    public void setLoginAdminAccount(String strAccountResult) {
        // json parse
        JSONObject jsonAccount;
        JSONObject jsonClubs;
        htLogin = new Hashtable<String, Hashtable<String, String>>();
        try {
            jsonAccount = new JSONObject(strAccountResult);
            // Log.d("accounts", jsonAccount.getString("accounts"));
            jsonClubs = new JSONObject(jsonAccount.getString("accounts"));

            Iterator iter = jsonClubs.keys();
            while(iter.hasNext()) {
                String club = (String) iter.next();
                JSONObject val = (JSONObject) jsonClubs.get(club);
                String id = (String) val.get("id");
                String pw = (String) val.get("pw");
                // Log.d("val", val.get("id") + "::" + val.get("pw"));
                setIdPw(htLogin, club, id, pw);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
    };
    public WebViewClient getSearchWebviewClient(String searchScript) {
        return new WebViewClient(){
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("script", "search webview loaded!!");
                view.loadUrl(searchScript);
                super.onPageFinished(view, url);
            }
            public void onConsoleMessage(ConsoleMessage message) {
                try{
                    mqtt.publish("TZLOG", message.message().getBytes(StandardCharsets.UTF_8), 0, false );
                } catch(MqttException e) {
                    e.printStackTrace();
                }
            }
        };
    };
    public void goToSearch() {
        Log.d("searchScript", searchScript);
        Log.d("searchUrl", searchUrl);
        // ?????? ?????? ??????
        queue.add(searchScript);
        wView.loadUrl(searchUrl);

        //finish();
    };
    public String getPostCall(String url, String param) {
        postURL = url;
        postParam = param;
        SearchActivity.CallThread ct = new SearchActivity.CallThread();
        ct.start();
        try {
            ct.join();
        } catch(Exception e) {
            e.printStackTrace();
        }

        // http response ??????
        String strResult = ct.getResult();
        // Log.d("RESULT", strResult);

        return strResult;
    };
    public class CallThread extends Thread {
        private String Result;
        public void run() {
            try{
                URL testUrl = new URL(postURL);
                HttpURLConnection urlConn = (HttpURLConnection) testUrl.openConnection();

                // [2-1]. urlConn ??????.
                urlConn.setConnectTimeout(15000);
                urlConn.setReadTimeout(5000);
                urlConn.setDoInput(true);
                urlConn.setDoOutput(true);
                urlConn.setUseCaches(false);
                urlConn.setRequestMethod("POST"); // URL ????????? ?????? ????????? ?????? : POST.
                urlConn.setRequestProperty("Accept-Charset", "UTF-8"); // Accept-Charset ??????.
                urlConn.setRequestProperty("Accept", "application/json");
                // urlConn.setRequestProperty("Context_Type", "application/x-www-form-urlencode");
                urlConn.setRequestProperty("Content-Type", "application/json");
                // urlConn.setRequestProperty("apikey", ""); // ""?????? apikey??? ??????


                // [2-2]. parameter ?????? ??? ????????? ????????????.
                String strParams = postParam; //sbParams??? ????????? ?????????????????? ??????????????? ??????. ???)id=id1&pw=123;
                OutputStream os = urlConn.getOutputStream();
                os.write(strParams.getBytes("UTF-8")); // ?????? ???????????? ??????.
                os.flush(); // ?????? ???????????? ?????????(?????????)?????? ????????? ??? ?????? ?????? ???????????? ?????? ??????.
                os.close(); // ?????? ???????????? ?????? ?????? ????????? ????????? ??????.

                // [2-3]. ?????? ?????? ??????.
                // ?????? ??? null??? ???????????? ???????????? ??????.
                if (urlConn.getResponseCode() != HttpURLConnection.HTTP_OK) return;

                // [2-4]. ????????? ????????? ??????.
                // ????????? URL??? ???????????? BufferedReader??? ?????????.
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                urlConn.getInputStream(),
                                "UTF-8"
                        )
                );

                // ???????????? ????????? ??? ?????? ?????? ??????.
                String line;
                String page = "";

                // ????????? ????????? ?????????.
                while ((line = reader.readLine()) != null){
                    page += line;
                }

                Result = page;

            } catch (MalformedURLException e) { // for URL.
                e.printStackTrace();
            } catch (IOException e) { // for openConnection().
                e.printStackTrace();
            } finally {
            }
        }
        public String getResult() {
            return this.Result;
        }
    }
    public void setIdPw(@NonNull Hashtable<String, Hashtable<String, String>> htLogin, String club, String id, String pw) {
        Hashtable<String, String> ht_island = new Hashtable<String, String>();
        ht_island.put("id", id);
        ht_island.put("pw", pw);
        htLogin.put(club, ht_island);
    }
    public String setStringTemplate(@NonNull Hashtable<String, String> params, String template) {
        for(String key:params.keySet()) {
            String val = params.get(key);
            // Log.d("param", key + "::" + val);
            String regex = "\\$\\{" + key + "\\}";
            template = template.replaceAll(regex, val);
        }
        // Log.d("template", template);
        return template;
    }
}
