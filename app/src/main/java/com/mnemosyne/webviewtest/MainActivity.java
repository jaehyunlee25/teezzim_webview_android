package com.mnemosyne.webviewtest;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Hashtable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    WebView wView;
    Button button;
    Spinner spinner;
    String postURL;
    String postParam;
    String urlHeader = "http://mnemosynesolutions.co.kr:8080/";
    // String urlHeader = "http://10.0.2.2:8080/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wView = (WebView) findViewById(R.id.wView);
        // initWebView();
        button = (Button) findViewById(R.id.button);
        initButton();
        spinner = (Spinner) findViewById(R.id.spinner);

        // call thread for clubs
        postURL = urlHeader + "clubs";
        postParam = "{}";
        CallThread ct = new CallThread();
        ct.start();
        try {
            ct.join();
        } catch(Exception e) {
            e.printStackTrace();
        }

        // http response 수신
        String strResult = ct.getResult();
        Log.d("RESULT", strResult);

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
            return;
        }

        ArrayAdapter<String> items = new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            list
        );
        spinner.setAdapter(items);
        initSpinner();
    }

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
                postURL = urlHeader + clubEngName;

                CallThread ct = new CallThread();
                ct.start();
                try {
                    ct.join();
                } catch(Exception e) {
                    e.printStackTrace();
                }

                // http response 수신
                String strResult = ct.getResult();
                Log.d("RESULT", strResult);

                // json parse
                JSONObject json;
                String scriptTemplate = "";
                String loginUrl = "";
                try {
                    json = new JSONObject(strResult);
                    Log.d("URL", json.getString("url"));
                    Log.d("script", json.getString("script"));
                    scriptTemplate = json.getString("script");
                    loginUrl = json.getString("url");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }

                // login hashtable
                Hashtable<String, Hashtable<String, String>> htLogin = new Hashtable<String, Hashtable<String, String>>();
                setIdPw(htLogin, "allday", "jhlee25", "ilovegolf778");
                setIdPw(htLogin, "bearsbest", "newrison", "ilovegolf778");
                setIdPw(htLogin, "delphino", "newrison", "ilovegolf778");
                setIdPw(htLogin, "dongchon", "jhlee25", "ilovegolf778");
                setIdPw(htLogin, "dreampark", "newrison", "ilovegolf778");

                setIdPw(htLogin, "hantan", "newrison", "ilovegolf778");
                setIdPw(htLogin, "hilldeloci", "newrison", "ilovegolf778");
                setIdPw(htLogin, "imperiallake", "newrison", "ilovegolf778");
                setIdPw(htLogin, "inchungrand", "mnemosyne", "ilovegolf778");
                setIdPw(htLogin, "island", "newrison", "ilovegolf778");

                setIdPw(htLogin, "jayuro", "jhlee25", "ilovegolf778");
                setIdPw(htLogin, "jinyang", "newrison", "ilovegolf778");
                setIdPw(htLogin, "lakeside", "newrison", "ilovegolf778");
                setIdPw(htLogin, "lakewood", "newrison", "ilovegolf778");
                setIdPw(htLogin, "midas_gumi", "mnemosyne", "ilovegolf778");

                setIdPw(htLogin, "midas_lake", "mnemosyne", "ilovegolf778");
                setIdPw(htLogin, "midas_valley", "mnemosyne", "ilovegolf778");
                setIdPw(htLogin, "montvert", "mnemosyne", "ilovegolf778");
                setIdPw(htLogin, "namchunchun", "mnemosyne", "ya2ssarama!");
                setIdPw(htLogin, "namyeoju", "newrison", "ilovegolf778");

                setIdPw(htLogin, "oxfield", "jhlee25", "ilovegolf778");
                setIdPw(htLogin, "paganica_KMH", "mnemosyne", "ya2ssarama!");
                setIdPw(htLogin, "paju_KMH", "mnemosyne", "ya2ssarama!");
                setIdPw(htLogin, "parkvalley", "jhlee25", "ilovegolf778");
                setIdPw(htLogin, "players", "newrison", "ilovegolf778");

                setIdPw(htLogin, "rainbowhills_KMH", "newrison", "ilovegolf778");
                setIdPw(htLogin, "royalforet", "jhlee25", "ilovegolf778");
                setIdPw(htLogin, "seowon", "newrison", "ilovegolf778");
                setIdPw(htLogin, "shilla_KMH", "newrison", "ilovegolf778");
                setIdPw(htLogin, "sky72", "newrison", "ilovegolf778");

                setIdPw(htLogin, "smartku", "newrison", "ilovegolf778");
                setIdPw(htLogin, "sophiagreen", "newrison", "ilovegolf778");
                setIdPw(htLogin, "southsprings", "mnemosyne", "ya2ssarama!");
                setIdPw(htLogin, "sunhill", "김덕우", "01071678790");
                setIdPw(htLogin, "tgv_KMH", "mnemosyne", "ya2ssarama!");

                setIdPw(htLogin, "uni_island", "jhlee25", "ilovegolf778");
                setIdPw(htLogin, "vivaldi_east", "newrison", "ilovegolf778");
                setIdPw(htLogin, "vivaldi_mountain", "newrison", "ilovegolf778");
                setIdPw(htLogin, "vivaldi_west", "newrison", "ilovegolf778");
                setIdPw(htLogin, "yongin", "newrison", "ya2ssarama!");

                // params into template script
                Hashtable<String, String> params = new Hashtable<String, String>();
                Hashtable<String, String> idpw = htLogin.get(clubEngName);
                params.put("login_id", idpw.get("id"));
                params.put("login_password", idpw.get("pw"));
                String loginScript = setStringTemplate(params, scriptTemplate);
                initWebView(loginUrl, loginScript);
            }
        });
    }
    public void initWebView(String url, String script) {
        wView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                view.loadUrl(script);
                super.onPageFinished(view, url);
            }
        });
        WebSettings ws = wView.getSettings();
        ws.setJavaScriptEnabled(true);

        // String strUrl = "https://www.islandresort.co.kr/html/member/login.asp?gopath=/html/reserve/reserve01.asp&b_idx=";
        wView.loadUrl(url);
    }
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
    public String setStringTemplate(Hashtable<String, String> params, String template) {
        for(String key:params.keySet()) {
            String val = params.get(key);
            Log.d("param", key + "::" + val);
            String regex = "\\$\\{" + key + "\\}";
            template = template.replaceAll(regex, val);
        }
        Log.d("template", template);
        return template;
    }
    public void setIdPw(Hashtable<String, Hashtable<String, String>> htLogin, String club, String id, String pw) {
        Hashtable<String, String> ht_island = new Hashtable<String, String>();
        ht_island.put("id", id);
        ht_island.put("pw", pw);
        htLogin.put(club, ht_island);
    }
}
