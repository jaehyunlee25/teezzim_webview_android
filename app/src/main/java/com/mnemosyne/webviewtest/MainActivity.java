package com.mnemosyne.webviewtest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Hashtable;

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

public class MainActivity extends AppCompatActivity {
    WebView wView;
    Button button;
    Button search;
    Spinner spinner;
    Hashtable<String, Hashtable<String, String>> htLogin;
    SQLiteDatabase sqlite;
    SharedPreferences spf;
    String urlHeader = "http://mnemosynesolutions.co.kr:8080/";
    String urlReservationHeader = "https://dev.mnemosyne.co.kr/";
    MqttAndroidClient mqtt;
    String urlMqtt = "tcp://dev.mnemosyne.co.kr:1883";
    // String urlHeader = "http://10.0.2.2:8080/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // preference
        spf = getSharedPreferences("DEVICE", MODE_PRIVATE);

        // mqtt
        mqtt = new MqttAndroidClient(this, urlMqtt, MqttClient.generateClientId());
        setMqtt();

        //sqlite
        AssetManager am = getAssets();
        DBHelper dbhp = new DBHelper(this, "teezzim", 1);
        sqlite = dbhp.getWritableDatabase();
        String sqlSitedata = "";
        try {
            Log.d("sqlite", "drop table Sitedata!!!");
            sqlite.execSQL("drop table if exists Sitedata");
            sqlSitedata = getFile(am, "sqls/create/Sitedata.sql");
            sqlite.execSQL(sqlSitedata);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("sqlite", "Sitedata created!!!");

        // 웹뷰
        wView = (WebView) findViewById(R.id.wView);
        WebSettings ws = wView.getSettings();
        ws.setJavaScriptEnabled(true);
        wView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                try{
                    Log.d("mqtt", "mqtt webview log!!" + message.message());
                    mqtt.publish("TZLOG", message.message().getBytes(StandardCharsets.UTF_8), 0, false );
                } catch(MqttException e) {
                    e.printStackTrace();
                }
                return super.onConsoleMessage(message);
            }
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                /*AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);

                adb.setTitle("");
                adb.setMessage(message);
                adb.setPositiveButton(
                    android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    }
                );
                adb.setCancelable(false);
                AlertDialog ad = adb.create();
                ad.show();*/
                result.confirm();


                return true;
            }
        });


        // 로그인 버튼
        button = (Button) findViewById(R.id.button);
        initButton();

        // 서치 버튼
        search = (Button) findViewById(R.id.btnSearch);
        initSearchButton();

        // 스피너
        String strResult = getPostCall(urlHeader + "clubs", "{}");
        ArrayAdapter<String> clubs = getSpinnerAdapter(strResult);
        spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setAdapter(clubs);
        initSpinner();

        // 로그인 관리자 계정
        String strAccountResult = getPostCall(urlHeader + "account", "{}");
        setLoginAdminAccount(strAccountResult);

        FirebaseMessaging
            .getInstance()
            .getToken()
            .addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    if (!task.isSuccessful()) {
                        Log.w("TAG", "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d("Token", token);

                    String UUID = spf.getString("UUID", "");
                    Log.d("pref", "UUID: " + UUID);
                    if(UUID.equals("")) {
                        // #1. 서버 api를 호출하여 uuid를 받아온다.
                        String param = "{\"token\": \"" + token + "\", \"type\": \"admin\"}";
                        String url = urlReservationHeader + "api/reservation/newDevice";
                        Log.d("pref", url + param);
                        String strResult = getPostCall(url, param);
                        // json parse
                        JSONObject json;
                        try {
                            json = new JSONObject(strResult);
                            UUID = json.getString("deviceUUID");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        // #2. 받아 온 uuid를 pref에 저장한다.
                        SharedPreferences.Editor editor = spf.edit();
                        editor.putString("UUID", UUID);
                        editor.commit();
                    }

                    String prevToken = spf.getString("token", "");
                    Log.d("pref", "prevToken: " + prevToken);
                    if(!prevToken.equals(token)) {
                        // #1. 서버 api를 호출한다.
                        String url = urlReservationHeader + "api/reservation/newToken";
                        String param = "{\"token\": \"" + token + "\", \"id\": \"" + UUID + "\"}";
                        String strResult = getPostCall(url, param);
                        // json parse
                        JSONObject json;
                        String code = "";
                        try {
                            json = new JSONObject(strResult);
                            code = json.getString("resultCode");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        if(code.equals("200")) {
                            // #2. preference에 token을 입력한다.
                            SharedPreferences.Editor editor = spf.edit();
                            editor.putString("token", token);
                            editor.commit();
                        }
                        Log.d("pref", "token not valid!!!");
                    } else {
                        Log.d("pref", "token valid!!!");
                    }
                    
                    // 서버에서 삭제
                    String clubUUID = "2ec1a5c2-e3eb-11ec-a93e-0242ac11000a";
                    String param = "{\"golf_club_id\": \"" + clubUUID + "\", \"id\": \"" + UUID + "\"}";
                    getPostCall(urlReservationHeader + "api/reservation/delGolfClubInDevice", param);

                    clubUUID = "b0ed2419-e3e4-11ec-a93e-0242ac11000a";
                    param = "{\"golf_club_id\": \"" + clubUUID + "\", \"id\": \"" + UUID + "\"}";
                    getPostCall(urlReservationHeader + "api/reservation/delGolfClubInDevice", param);

                    clubUUID = "45d852b4-e3d8-11ec-a93e-0242ac11000a";
                    param = "{\"golf_club_id\": \"" + clubUUID + "\", \"id\": \"" + UUID + "\"}";
                    getPostCall(urlReservationHeader + "api/reservation/delGolfClubInDevice", param);

                    
                    // 서버에 등록
                    clubUUID = "2ec1a5c2-e3eb-11ec-a93e-0242ac11000a";
                    param = "{\"golf_club_id\": \"" + clubUUID + "\", \"id\": \"" + UUID + "\"}";
                    String strResult = getPostCall(urlReservationHeader + "api/reservation/newGolfClubInDevice", param);
                    String code = getCodeFromResult(strResult);
                    if(code.equals("200")) {
                        // sqlite에 등록
                        setInTable("e6eeccf1-e3e4-11ec-a93e-0242ac11000a","newrison","ilovegolf778",clubUUID);
                    }
                    clubUUID = "b0ed2419-e3e4-11ec-a93e-0242ac11000a";
                    param = "{\"golf_club_id\": \"" + clubUUID + "\", \"id\": \"" + UUID + "\"}";
                    strResult = getPostCall(urlReservationHeader + "api/reservation/newGolfClubInDevice", param);
                    code = getCodeFromResult(strResult);
                    if(code.equals("200")) {
                        // sqlite에 등록
                        setInTable("e6eeccf1-e3e4-11ec-a93e-0242ac11000a", "newrison", "ilovegolf778", clubUUID);
                    }

                    clubUUID = "45d852b4-e3d8-11ec-a93e-0242ac11000a";
                    param = "{\"golf_club_id\": \"" + clubUUID + "\", \"id\": \"" + UUID + "\"}";
                    strResult = getPostCall(urlReservationHeader + "api/reservation/newGolfClubInDevice", param);
                    code = getCodeFromResult(strResult);
                    if(code.equals("200")) {
                        // sqlite에 등록
                        setInTable("7377e629-e3d8-11ec-a93e-0242ac11000a", "newrison", "ya2ssarama!", clubUUID);
                    }

                    // Log and toast
                    // String msg = getString(R.string.msg_token_fmt, token);
                    // Log.d("TAG", msg);
                    // Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        FirebaseMessaging
            .getInstance()
            .subscribeToTopic("admin")
            .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    String msg = "admin successful subscribe!";
                    if(!task.isSuccessful()) {
                        msg = "subscribed failed!";
                    }
                    Log.d("MSG", msg);
                }
            });

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
    public String getCodeFromResult(String strResult) {
        // json parse
        JSONObject json;
        String code = "";
        try {
            json = new JSONObject(strResult);
            code = json.getString("resultCode");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return code;
    };
    public void setInTable(String Course, String Login_id, String Login_pw, String Club_id) {
        ContentValues cvs = new ContentValues();
        cvs.put("Course", Course);
        cvs.put("Login_id", Login_id);
        cvs.put("Login_pw", Login_pw);
        cvs.put("Club_id", Club_id);
        sqlite.insert("Sitedata", null, cvs);
        Log.d("sqlite", Course + Login_id + Login_pw + Club_id);
    };
    public String getFile(AssetManager am, String filename) throws IOException {
        InputStream is = am.open(filename);
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(isr);
        StringBuffer buffer = new StringBuffer();
        String line = reader.readLine();
        while(line != null) {
            buffer.append(line+"\r\n");
            line = reader.readLine();
        }
        String result = buffer.toString();
        return result;
    }
    public class AndroidController {
        final public Handler handler = new Handler();
        @JavascriptInterface
        public void message(final String message) {
            Log.d("message from webview", message);
            handler.post(new Runnable() {

                @Override
                public void run() {
                    wView.loadUrl("javascript:(() => { console.log('end'); })();");
                    // if(message.equals("end of procGolfSchedule!")) finish();
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
    public ArrayAdapter<String> getSpinnerAdapter(String strResult) {
        // json parse
        JSONObject json;
        ArrayList<String> list = new ArrayList();;
        try {
            json = new JSONObject(strResult);
            Log.d("clubs", json.getString("clubs"));
            JSONArray ja = new JSONArray(json.getString("clubs"));
            for (int i = 0; i < ja.length(); i++) {
                list.add(ja.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // set spinner
        ArrayAdapter<String> items = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                list
        );

        return items;
    };
    public void initSpinner() {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String item = (String) spinner.getItemAtPosition(position);
                Log.d("item:", item);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    public void initButton() {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String clubEngName = spinner.getSelectedItem().toString();
                String strResult = getPostCall(urlHeader + clubEngName, "{}");
                // json parse
                JSONObject json;
                String scriptTemplate = "";
                String loginUrl = "";
                try {
                    json = new JSONObject(strResult);
                    scriptTemplate = json.getString("script");
                    loginUrl = json.getString("url");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }

                // params into template script
                String deviceId = spf.getString("UUID", "");
                String deviceToken = spf.getString("token", "");
                Hashtable<String, String> params = new Hashtable<String, String>();
                Hashtable<String, String> idpw = htLogin.get(clubEngName);

                params.put("deviceId", deviceId);
                params.put("deviceToken", deviceToken);
                params.put("login_id", idpw.get("id"));
                params.put("login_password", idpw.get("pw"));
                String loginScript = setStringTemplate(params, scriptTemplate);
                Log.d("login", loginScript);

                WebViewClient wvc = getLoginWebviewClient(loginScript);
                wView.setWebViewClient(wvc);
                wView.loadUrl(loginUrl);
            }
        });
    }
    public void initSearchButton() {
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String clubEngName = spinner.getSelectedItem().toString();
                String param = "{\"club\": \"" + clubEngName + "\"}";
                String strResult = getPostCall(urlHeader + "search", param);
                // json parse
                JSONObject json;
                String scriptTemplate = "";
                String searchUrl = "";
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

                String searchScript = setStringTemplate(params, scriptTemplate);
                Log.d("script", searchScript);
                // String searchScript = "javascript:(() => {changeCoDiv(\"76\");})()";

                WebViewClient wvc = getSearchWebviewClient(searchScript);
                wView.setWebViewClient(wvc);
                wView.loadUrl(searchUrl);
            }
        });
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
        };
    };
    public WebViewClient getLoginWebviewClient(String loginScript) {
        return new WebViewClient(){
            int loginToggle = 1;
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("script", "login webview loaded!!");
                if(loginToggle == 1) {
                    view.loadUrl(loginScript);
                    loginToggle = 0;
                }
                super.onPageFinished(view, url);
            }
        };
    };
    public String getPostCall(String url, String param) {
        CallThread ct = new CallThread(url, param);
        ct.start();
        try {
            ct.join();
        } catch(Exception e) {
            e.printStackTrace();
        }

        // http response 수신
        String strResult = ct.getResult();
        Log.d("RESULT", strResult);

        return strResult;
    };
    public class CallThread extends Thread {
        private String threadUrl;
        private String threadParam;
        private String Result;
        public CallThread (String url, String param) {
            threadUrl = url;
            threadParam = param;
        };
        public void run() {
            try{
                URL testUrl = new URL(threadUrl);
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
                String strParams = threadParam; //sbParams에 정리한 파라미터들을 스트링으로 저장. 예)id=id1&pw=123;
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
            Log.d("param", key + "::" + val);
            String regex = "\\$\\{" + key + "\\}";
            template = template.replaceAll(regex, val);
        }
        Log.d("template", template);
        return template;
    }
    public void setIdPw(@NonNull Hashtable<String, Hashtable<String, String>> htLogin, String club, String id, String pw) {
        Hashtable<String, String> ht_island = new Hashtable<String, String>();
        ht_island.put("id", id);
        ht_island.put("pw", pw);
        htLogin.put(club, ht_island);
    }
}
