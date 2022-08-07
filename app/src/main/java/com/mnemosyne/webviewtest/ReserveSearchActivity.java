package com.mnemosyne.webviewtest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
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
import java.util.Hashtable;
import java.util.Iterator;

public class ReserveSearchActivity extends AppCompatActivity {
    WebView wView;
    SharedPreferences spf;
    MqttAndroidClient mqtt;
    Hashtable<String, Hashtable<String, String>> htLogin;
    String postURL;
    String postParam;
    String reserveUrl = "";
    String reserveSearchScript = "(() => {})();";
    String urlMqtt = "tcp://dev.mnemosyne.co.kr:1883";
    String urlHeader = "http://mnemosynesolutions.co.kr:8080/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reserve_search);

        // 상단 타이틀 변경
        setTitle("teezzim reserve search by MQTT");

        // preference
        spf = getSharedPreferences("DEVICE", MODE_PRIVATE);

        // mqtt
        mqtt = new MqttAndroidClient(this, urlMqtt, MqttClient.generateClientId());
        setMqtt();

        // 서비스로부터 자료 수신
        Intent service = getIntent();
        String clubEngName = service.getStringExtra("club");
        String clubId = service.getStringExtra("club_id");

        JSONObject prm = new JSONObject();
        try {
            prm.put("club", clubEngName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String param = prm.toString();
        String strResult = getPostCall(urlHeader + "reserveSearchbot_admin", param);
        Log.d("script", strResult);
        // json parse
        JSONObject json;
        String scriptTemplate;
        try {
            json = new JSONObject(strResult);
            scriptTemplate = json.getString("script");
            reserveUrl = json.getString("url");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        // 로그인 관리자 계정
        String strAccountResult = getPostCall(urlHeader + "account", "{}");
        setLoginAdminAccount(strAccountResult);

        // params into template script
        String deviceId = spf.getString("UUID", "");
        String deviceToken = spf.getString("token", "");
        Hashtable<String, String> params = new Hashtable<String, String>();
        Hashtable<String, String> idpw = htLogin.get(clubEngName);
        params.put("deviceId", deviceId);
        params.put("deviceToken", deviceToken);
        params.put("golfClubId", clubId);
        params.put("login_id", idpw.get("id"));
        params.put("login_password", idpw.get("pw"));

        reserveSearchScript = setStringTemplate(params, scriptTemplate);
        Log.d("reserve", reserveSearchScript);

        // 웹뷰
        wView = (WebView) findViewById(R.id.wView);
        WebSettings ws = wView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);

        WebViewClient wvc = getSearchWebviewClient(reserveSearchScript);
        wView.setWebViewClient(wvc);
        wView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                try{
                    //Log.d("mqtt", "mqtt webview log!!" + message.message());
                    byte[] bts = message.message().getBytes(StandardCharsets.UTF_8);
                    mqtt.publish("TZLOG", bts, 0, false );
                    //Log.d("mqtt", "mqtt webview log end!!" + message.message());
                } catch(MqttException e) {
                    Log.d("mqtt", "mqtt webview mqtt error!!" + message.message());
                    e.printStackTrace();
                } catch(Exception e) {
                    Log.d("mqtt", "mqtt webview other error!!" + message.message());
                    e.printStackTrace();
                }
                return super.onConsoleMessage(message);
            }
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                Log.d("jsLog", message);
                result.confirm();
                return true;
            }
            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                Log.d("jsConfirm", message);
                result.confirm();
                return true;
            }
        });
        wView.loadUrl(reserveUrl);

        ReserveSearchActivity.AndroidController ac = new ReserveSearchActivity.AndroidController();
        wView.addJavascriptInterface(ac, "AndroidController");

    }
    public void setLoginAdminAccount(String strAccountResult) {
        // json parse
        JSONObject jsonAccount;
        JSONObject jsonClubs;
        htLogin = new Hashtable<String, Hashtable<String, String>>();
        try {
            jsonAccount = new JSONObject(strAccountResult);
            Log.d("accounts", jsonAccount.getString("accounts"));
            jsonClubs = new JSONObject(jsonAccount.getString("accounts"));

            Iterator iter = jsonClubs.keys();
            while(iter.hasNext()) {
                String club = (String) iter.next();
                JSONObject val = (JSONObject) jsonClubs.get(club);
                String id = (String) val.get("id");
                String pw = (String) val.get("pw");
                Log.d("val", val.get("id") + "::" + val.get("pw"));
                setIdPw(htLogin, club, id, pw);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
    };
    public void setIdPw(@NonNull Hashtable<String, Hashtable<String, String>> htLogin, String club, String id, String pw) {
        Hashtable<String, String> ht_island = new Hashtable<String, String>();
        ht_island.put("id", id);
        ht_island.put("pw", pw);
        htLogin.put(club, ht_island);
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
                    if(message.equals("end of reserve/search")) finish();
                    if(message.equals("TZ_MSG_IC")) finish();
                }
            });
        };
    };
    public String getPostCall(String url, String param) {
        postURL = url;
        postParam = param;
        ReserveSearchActivity.CallThread ct = new ReserveSearchActivity.CallThread();
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
    public class CallThread extends Thread {
        private String Result;
        public void run() {
            try{
                URL testUrl = new URL(postURL);
                HttpURLConnection urlConn = (HttpURLConnection) testUrl.openConnection();

                // [2-1]. urlConn 설정.
                urlConn.setConnectTimeout(15000);
                urlConn.setReadTimeout(5000);
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
