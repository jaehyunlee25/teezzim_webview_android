package com.mnemosyne.webviewtest;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage rm) {
        showNotification(rm.getData().get("title"), rm.getData().get("message"));

        Log.d("command", rm.getData().get("command"));
        Log.d("club", rm.getData().get("club"));

        Intent intent = new Intent(this, SearchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("club", rm.getData().get("club"));
        startActivity(intent);

    }
    private void showNotification(String title, String message) {
        // R.layout.activity_main
        Application app = getApplication();
        app.getApplicationContext();
    }

    @Override
    public void onNewToken(String s) {
        super.onNewToken(s);
    }

}
