package com.ilp.innovations.myilp;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Calendar;


public class ILPService extends WearableListenerService{

    private final String MESSAGE1_PATH = "/message_path";
    private String remoteNodeId;
    private boolean isOperationInProgress=false;
    private GoogleApiClient mGoogleApiClient;

    public ILPService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API).build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        remoteNodeId = messageEvent.getSourceNodeId();
        if(!MainActivity.isAppForeground) {
            Log.d("myTag","Received message : "+new String(messageEvent.getData()));
            final String message = new String(messageEvent.getData());
            final Calendar cal = Calendar.getInstance();
            String today = String.valueOf(cal.get(Calendar.YEAR))+"-"
                    +String.valueOf(cal.get(Calendar.MONTH)+1)+"-"
                    +String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
            if(!isOperationInProgress) {
                new LongOperation_getSchedule(
                        "http://theinspirer.in/ilpscheduleapp/schedulelist_json.php",
                        message, today).execute("");
                isOperationInProgress = true;
            }
        }

    }

    public void sendMessage(String message) {
        Log.d("myTag","Sending reply to "+remoteNodeId);
        Wearable.MessageApi.sendMessage(mGoogleApiClient,remoteNodeId,MESSAGE1_PATH,message.getBytes())
                .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                if (sendMessageResult.getStatus().isSuccess()) {
                    Log.d("myTag","Message sent successfully");
                } else {
                    Log.d("myTag","Message sending failed");
                }
            }
        });
    }


    private class LongOperation_getSchedule extends AsyncTask<String, Void, String> {
        String _url;
        String _batch, _date;
        private String Content;
        private String Error = null;
        String data = "";
        String[] values;
        Integer[] images;

        public LongOperation_getSchedule(String url, String batch,
                                         String date) {
            _url = url;
            _batch = batch;
            _date = date;
        }

        protected String doInBackground(String... urls) {

            BufferedReader reader = null;
            try {
                data = URLEncoder.encode("batch", "UTF-8") + "="
                        + URLEncoder.encode(_batch.trim(), "UTF-8");
                data += "&" + URLEncoder.encode("date", "UTF-8") + "="
                        + URLEncoder.encode(_date, "UTF-8");
                URL url = new URL(_url);
                URLConnection conn = url.openConnection();
                conn.setDoOutput(true);
                OutputStreamWriter wr = new OutputStreamWriter(
                        conn.getOutputStream());
                wr.write(data);
                wr.flush();
                reader = new BufferedReader(new InputStreamReader(
                        conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                Content = sb.toString();
            } catch (Exception ex) {
                Error = ex.getMessage();
            } finally {
                try {
                    reader.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            return "";
        }

        protected void onPostExecute(String unused) {
            // NOTE: You can call UI Element here.
            if (Error != null) {
                sendMessage("error:Network not available at device");

            } else {
                String result = "";
                JSONObject jsonResponse;
                try {
                    if (Content != null) {
                        jsonResponse = new JSONObject(Content);
                        Log.d("RESPONSE---->", jsonResponse.toString());
                        JSONArray jsonMainNode = jsonResponse
                                .optJSONArray("Android");
                        int lengthJsonArr = jsonMainNode.length();
                        values = new String[lengthJsonArr];
                        images = new Integer[lengthJsonArr];
                        for (int i = 0; i < lengthJsonArr; i++) {
                            JSONObject jsonChildNode = jsonMainNode
                                    .getJSONObject(i);
                            String batch = jsonChildNode.optString("batch");
                            String slot = jsonChildNode.optString("slot");
                            String course = jsonChildNode.optString("course");
                            String faculty = jsonChildNode.optString("faculty");
                            String room = jsonChildNode.optString("room");
                            result = jsonChildNode.optString("result");
                            sendMessage("success:" + course + ":" + faculty + ":" + slot + ":" + room);
                        }
                        if (result.equalsIgnoreCase("success")) {
                            // do your success work
                        } else {
                            sendMessage("error:No schedule found!");
                        }
                    } else {
                        sendMessage("error:Network not available at device");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            isOperationInProgress = false;
        }
    }

}
