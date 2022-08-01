package com.mnemosyne.webviewtest;

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
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.nio.charset.StandardCharsets;

public class SearchAll extends AppCompatActivity {
    LinearLayout layout;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_all);

        // 상단 타이틀 변경
        setTitle("teezzim multi dynamic webview");

        layout = findViewById(R.id.cover);

        WebView webView = new WebView(getApplication());
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        WebViewClient wvc = new WebViewClient(){
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d("script", "search webview loaded!!");
                // view.loadUrl(searchScript);
                super.onPageFinished(view, url);
            }
        };
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                try{
                } catch(Exception e) {
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
        AndroidController ac = new AndroidController(webView);
        webView.addJavascriptInterface(ac, "AndroidController");

        layout.addView(webView);
    }
    public class AndroidController {
        final public Handler handler = new Handler();
        private WebView WEBVIEW;
        public AndroidController (WebView wv) {
            WEBVIEW = wv;
        };
        @JavascriptInterface
        public void message(final String message) {
            Log.d("message from webview", message);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    WEBVIEW.loadUrl("javascript:(() => { console.log('end'); })();");
                    // if(message.equals("end of procGolfSchedule!")) finish();
                }
            });
        };
    };
}
