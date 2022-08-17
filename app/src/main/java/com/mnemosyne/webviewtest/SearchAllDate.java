package com.mnemosyne.webviewtest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class SearchAllDate extends AppCompatActivity {
    LinearLayout layout;
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
    Hashtable<String, String> callbackClubs;
    String deviceId;

    String urlMqtt = "tcp://dev.mnemosyne.co.kr:1883";
    // String urlHeader = "http://mnemosynesolutions.co.kr:8080/";
    String urlHeader = "http://dev.mnemosyne.co.kr:1009/";
    Integer callback_count = 0;
    // String urlHeader = "http://10.0.2.2:8080/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_all);
        // 상단 타이틀 변경
        setTitle("teezzim search by FCM");

        // preference
        spf = getSharedPreferences("DEVICE", MODE_PRIVATE);
        deviceId = spf.getString("UUID", "");

        // mqtt
        mqtt = new MqttAndroidClient(this, urlMqtt, MqttClient.generateClientId());
        setMqtt();

        // 로그인 관리자 계정
        String strAccountResult = getPostCall(urlHeader + "account", "{}");
        setLoginAdminAccount(strAccountResult);

        // 서비스로부터 자료 수신
        Intent service = getIntent();
        String strClubs = service.getStringExtra("clubs");
        Log.d("clubs", strClubs);
        strClubs = strClubs.substring(1, strClubs.length() - 1);
        strClubs = strClubs.replaceAll("\"", "");
        Log.d("clubs", strClubs);
        List<String> arrClubs = new ArrayList<String>(Arrays.asList(strClubs.split(",")));
        Log.d("TZ_CLUBS", arrClubs.toString());
        callbackClubs = new Hashtable<String, String>();

        layout = findViewById(R.id.cover);
        JSONObject prm = new JSONObject();
        JSONArray arr = new JSONArray();
        try {
            for(int i = 0; i < arrClubs.size(); i++) {
                callbackClubs.put(arrClubs.get(i), "");
                arr.put(arrClubs.get(i));
            }
            prm.put("clubs", arr);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String param = prm.toString();
        String strResult = getPostCall(urlHeader + "searchbots_date_admin", param);

        // json parse
        JSONObject json;
        JSONObject scripts;
        JSONObject urls;
        JSONObject UUIDs;

        Log.d("TZ_LENGTH", strResult);
        try {
            json = new JSONObject(strResult);

            scripts = new JSONObject(json.getString("scripts"));
            urls = new JSONObject(json.getString("urls"));
            UUIDs = new JSONObject(json.getString("ids"));


            Log.d("clubs", json.getString("scripts"));
            Log.d("clubs", json.getString("urls"));
            Log.d("clubs", json.getString("ids"));
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        ArrayList<WebView> wvList = new ArrayList<WebView>();
        for(int i = 0; i < arrClubs.size(); i++) {
            Log.d("webview", i+"");
            WebView wv = new WebView(getApplication());
            String club= arrClubs.get(i);

            // params into template script
            String deviceId = spf.getString("UUID", "");
            String deviceToken = spf.getString("token", "");
            Hashtable<String, String> params = new Hashtable<String, String>();
            Hashtable<String, String> idpw = htLogin.get(club);
            Log.d("club", club + " : " + idpw.get("id") + " : " + idpw.get("pw"));
            params.put("deviceId", deviceId);
            params.put("deviceToken", deviceToken);
            params.put("login_id", idpw.get("id"));
            params.put("login_password", idpw.get("pw"));

            try {
                params.put("golfClubId", UUIDs.getString(club));
                String script = setStringTemplate(params, scripts.getString(club));
                setWebView(wv, club, script);
                layout.addView(wv);
                wv.loadUrl(urls.getString(club));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            AndroidController ac = new AndroidController(wv, club);
            wv.addJavascriptInterface(ac, "AndroidController");
        }

    }
    public String getLogParam(String deviceId, String clubId, String msgType, String message) {
        JSONObject prm = new JSONObject();
        try {
            prm.put("deviceId", deviceId);
            prm.put("subType", msgType);
            prm.put("clubId", clubId);
            prm.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return prm.toString();
    };
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
    public void setWebView(WebView wv, String club, String script) {
        Log.d("MSG", club);
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 0;
        params.width = 10;
        params.height = 10;
        wv.setLayoutParams(params);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        WebViewClient wvc = new WebViewClient(){
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                // Log.d("jsLog", script + " :: " + club);
                view.loadUrl(script);
                super.onPageFinished(view, url);
            }
        };
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                try{
                    //Log.d("mqtt", "mqtt webview log!!" + message.message());
                    String param = getLogParam(deviceId, club,"console", message.message());
                    byte[] bts = param.getBytes(StandardCharsets.UTF_8);
                    mqtt.publish("TZLOG", bts, 0, false );
                } catch(Exception e) {
                    e.printStackTrace();
                }
                return super.onConsoleMessage(message);
            }
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                Log.d("jsAlert", message + " :: " + club);
                try{
                    //Log.d("mqtt", "mqtt webview log!!" + message.message());
                    String param = getLogParam(deviceId, club,"jsAlert", message);
                    byte[] bts = param.getBytes(StandardCharsets.UTF_8);
                    mqtt.publish("TZLOG", bts, 0, false );
                    //Log.d("mqtt", "mqtt webview log end!!" + message.message());
                } catch(MqttException e) {
                    Log.d("mqtt", "mqtt webview mqtt error!!" + message);
                    e.printStackTrace();
                } catch(Exception e) {
                    Log.d("mqtt", "onJsAlert error!!" + message);
                    e.printStackTrace();
                }
                result.confirm();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                Log.d("jsConfirm", message + " :: " + club);
                try{
                    //Log.d("mqtt", "mqtt webview log!!" + message.message());
                    String param = getLogParam(deviceId, club,"jsConfirm", message);
                    byte[] bts = param.getBytes(StandardCharsets.UTF_8);
                    mqtt.publish("TZLOG", bts, 0, false );
                    //Log.d("mqtt", "mqtt webview log end!!" + message.message());
                } catch(MqttException e) {
                    Log.d("mqtt", "mqtt webview mqtt error!!" + message);
                    e.printStackTrace();
                } catch(Exception e) {
                    Log.d("mqtt", "onJsConfirm error!!" + message);
                    e.printStackTrace();
                }
                result.confirm();
                return true;
            }
        });
        wv.setWebViewClient((wvc));
        AndroidController ac = new AndroidController(wv, club);
        wv.addJavascriptInterface(ac, "AndroidController");
    };
    public class AndroidController {
        final public Handler handler = new Handler();
        private WebView WEBVIEW;
        private String CLUB;
        public AndroidController (WebView wv, String club) {
            WEBVIEW = wv;
            CLUB = club;
        };

        @JavascriptInterface
        public void message(final String message) {
            Log.d("message from webview", message);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if(message.equals("end of procGolfSchedule!")) {
                        callback_count++;
                        Log.d("callback", "normal: " + CLUB + " : " + callback_count);
                        callbackClubs.put(CLUB, "normal");

                        Enumeration<String> enumKey = callbackClubs.keys();
                        while(enumKey.hasMoreElements()){
                            String key = enumKey.nextElement();
                            String val = callbackClubs.get(key);
                            Log.d("clubs", key + " : " + val);
                        }
                        layout.removeView(WEBVIEW);
                    }
                    if(message.equals("TZ_MSG_IC")) {
                        callback_count++;
                        Log.d("callback", "IC: " + CLUB + " : " + callback_count);
                        callbackClubs.put(CLUB, "IC");
                        Enumeration<String> enumKey = callbackClubs.keys();
                        while(enumKey.hasMoreElements()){
                            String key = enumKey.nextElement();
                            String val = callbackClubs.get(key);
                            Log.d("clubs", key + " : " + val);
                        }
                        layout.removeView(WEBVIEW);
                    }
                    if(message.equals("SUCCESS_OF_GET_DATE")) {
                        callback_count++;
                        Log.d("callback", "SUCCESS_OF_GET_DATE: " + CLUB + " : " + callback_count);
                        callbackClubs.put(CLUB, "SUCCESS_OF_GET_DATE");
                        Enumeration<String> enumKey = callbackClubs.keys();
                        while(enumKey.hasMoreElements()){
                            String key = enumKey.nextElement();
                            String val = callbackClubs.get(key);
                            Log.d("clubs", key + " : " + val);
                        }
                        layout.removeView(WEBVIEW);
                    }
                    if(message.equals("FAIL_OF_GET_DATE")) {
                        callback_count++;
                        Log.d("callback", "FAIL_OF_GET_DATE: " + CLUB + " : " + callback_count);
                        callbackClubs.put(CLUB, "FAIL_OF_GET_DATE");
                        Enumeration<String> enumKey = callbackClubs.keys();
                        while(enumKey.hasMoreElements()){
                            String key = enumKey.nextElement();
                            String val = callbackClubs.get(key);
                            Log.d("clubs", key + " : " + val);
                        }
                        layout.removeView(WEBVIEW);
                    }
                    if(callback_count == callbackClubs.size()) {
                        Log.d("mqtt", "finish()");
                        setResult (RESULT_OK);
                        finish();
                    }
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
    public String getPostCall(String url, String param) {
        postURL = url;
        postParam = param;
        SearchAllDate.CallThread ct = new SearchAllDate.CallThread();
        ct.start();
        try {
            ct.join();
        } catch(Exception e) {
            e.printStackTrace();
        }

        // http response 수신
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

                // [2-1]. urlConn 설정.
                urlConn.setConnectTimeout(15000);
                urlConn.setReadTimeout(10000);
                urlConn.setDoInput(true);
                urlConn.setDoOutput(true);
                urlConn.setUseCaches(false);
                urlConn.setRequestMethod("POST"); // URL 요청에 대한 메소드 설정 : POST.
                urlConn.setRequestProperty("Accept-Charset", "UTF-8"); // Accept-Charset 설정.
                urlConn.setRequestProperty("Accept", "application/json");
                // urlConn.setRequestProperty("Context_Type", "application/x-www-form-urlencode");
                urlConn.setRequestProperty("Content-Type", "application/json");
                // urlConn.setRequestProperty("apikey", ""); // ""안에 apikey를 입력


                // [2-2]. parameter 전달 및 데이터 읽어오기.
                String strParams = postParam; //sbParams에 정리한 파라미터들을 스트링으로 저장. 예)id=id1&pw=123;
                OutputStream os = urlConn.getOutputStream();
                os.write(strParams.getBytes("UTF-8")); // 출력 스트림에 출력.
                os.flush(); // 출력 스트림을 플러시(비운다)하고 버퍼링 된 모든 출력 바이트를 강제 실행.
                os.close(); // 출력 스트림을 닫고 모든 시스템 자원을 해제.

                // [2-3]. 연결 요청 확인.
                // 실패 시 null을 리턴하고 메서드를 종료.
                if (urlConn.getResponseCode() != HttpURLConnection.HTTP_OK) return;

                // [2-4]. 읽어온 결과물 리턴.
                // 요청한 URL의 출력물을 BufferedReader로 받는다.
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                urlConn.getInputStream(),
                                "UTF-8"
                        )
                );

                // 출력물의 라인과 그 합에 대한 변수.
                String line;
                String page = "";

                // 라인을 받아와 합친다.
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
