package com.mnemosyne.webviewtest;

import android.app.Application;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    SQLiteDatabase sqlite;
    @Override
    public void onMessageReceived(RemoteMessage rm) {
        showNotification(rm.getData().get("title"), rm.getData().get("message"));

        Log.d("command", rm.getData().get("command"));
        Log.d("club", rm.getData().get("club"));

        //sqlite
        AssetManager am = getAssets();
        DBHelper dbhp = new DBHelper(this, "teezzim", 1);
        sqlite = dbhp.getWritableDatabase();
        String sqlGolfClub = "";
        try {
            sqlGolfClub = getFile(am, "sqls/search/getGolfClub.sql");
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONArray ja = getFromTable(sqlGolfClub);

        Intent intent = new Intent(this, SearchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("club", rm.getData().get("club"));
        startActivity(intent);

    }
    public JSONArray getFromTable(String sql) {
        Cursor cursor = sqlite.rawQuery(sql, null);
        JSONArray resultSet = new JSONArray();
        if(cursor.getCount() == 0) {
            Log.d("sqlite", "no data in Sitedata!");
        } else {
            while(cursor.moveToNext()) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put(cursor.getColumnName(0), cursor.getString(0));
                    obj.put(cursor.getColumnName(1), cursor.getString(1));
                    obj.put(cursor.getColumnName(2), cursor.getString(2));
                    obj.put(cursor.getColumnName(3), cursor.getString(3));
                    obj.put(cursor.getColumnName(4), cursor.getString(4));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                resultSet.put(obj);
            }
            cursor.close();
            Log.d("sqlite", resultSet.toString());
        }
        return resultSet;
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
