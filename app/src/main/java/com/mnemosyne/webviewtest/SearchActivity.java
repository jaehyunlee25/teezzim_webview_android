package com.mnemosyne.webviewtest;

import static java.lang.Thread.sleep;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Handler;
import android.webkit.JavascriptInterface;

public class SearchActivity  extends AppCompatActivity {
    WebView wView;
    Queue<String> queue = new LinkedList<>();
    String postURL;
    String postParam;
    String searchUrl = "";
    String searchScript = "(() => {})();";
    Hashtable<String, Hashtable<String, String>> htLogin;
    String urlHeader = "http://mnemosynesolutions.co.kr:8080/";
    // String urlHeader = "http://10.0.2.2:8080/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        // 상단 타이틀 변경
        setTitle("teezzim search by FCM");
        // 로그인 관리자 계정
        String strAccountResult = getPostCall(urlHeader + "account", "{}");
        setLoginAdminAccount(strAccountResult);
        // 서비스로부터 자료 수신
        Intent service = getIntent();
        String clubEngName = service.getStringExtra("club");
        
        // 로그인 스크립트
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
        Hashtable<String, String> params = new Hashtable<String, String>();
        Hashtable<String, String> idpw = htLogin.get(clubEngName);
        params.put("login_id", idpw.get("id"));
        params.put("login_password", idpw.get("pw"));
        String loginScript = setStringTemplate(params, scriptTemplate);
        // 큐에 자료 삽입
        queue.add(loginScript);

        String param = "{\"club\": \"" + clubEngName + "\"}";
        strResult = getPostCall(urlHeader + "search", param);
        // json parse

        try {
            json = new JSONObject(strResult);
            scriptTemplate = json.getString("script");
            searchUrl = json.getString("url");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        // params into template script
        searchScript = scriptTemplate;
        // Log.d("script", searchScript);
        // 큐에 자료 삽입
        queue.add(searchUrl);
        queue.add(searchScript);

        // 웹뷰
        wView = (WebView) findViewById(R.id.wView);
        WebSettings ws = wView.getSettings();
        ws.setJavaScriptEnabled(true);

        WebViewClient wvc = getSearchWebviewClient();
        wView.setWebViewClient(wvc);
        wView.loadUrl(loginUrl);

        AndroidController ac = new AndroidController();
        wView.addJavascriptInterface(ac, "AndroidController");

    }
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
    public WebViewClient getSearchWebviewClient() {
        return new WebViewClient(){
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("script", "search webview loaded!!");
                String script = "";
                if(queue.peek() != null)  {
                    script = queue.poll();
                    view.loadUrl(script);
                }

                super.onPageFinished(view, url);
            }
        };
    };
    public void goToSearch() {
        Log.d("searchScript", searchScript);
        Log.d("searchUrl", searchUrl);
        // 큐에 자료 삽입
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
                urlConn.setReadTimeout(5000);
                urlConn.setDoInput(true);
                urlConn.setDoOutput(true);
                urlConn.setUseCaches(false);
                urlConn.setRequestMethod("POST"); // URL 요청에 대한 메소드 설정 : POST.
                urlConn.setRequestProperty("Accept-Charset", "UTF-8"); // Accept-Charset 설정.
                urlConn.setRequestProperty("Context_Type", "application/x-www-form-urlencode");
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
