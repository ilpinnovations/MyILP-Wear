package com.ilp.innovations.myilp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Calendar;

public class MainActivity extends Activity {
    private final String MESSAGE1_PATH = "/message_path";
    private final String MESSAGE2_PATH = "/message2";

    private TextView receivedMessagesText;
    private View message1Button;
    //private View message2Button;
    private GoogleApiClient apiClient;
    private NodeApi.NodeListener nodeListener;
    private String remoteNodeId;
    private MessageApi.MessageListener messageListener;
    private Handler handler;
    public static boolean isAppForeground = true;

    private boolean isOperationInProgress = false;
    private boolean isPaired = false;
    private ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();

        progress = new ProgressDialog(this);
        progress.setIndeterminate(true);
        progress.setMessage("Waiting for schedule");

        receivedMessagesText = (TextView) findViewById(R.id.welcomeMsg);
        message1Button = findViewById(R.id.sendBtn);

        message1Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "pair";
                Wearable.MessageApi.sendMessage(apiClient, remoteNodeId, MESSAGE1_PATH, message.getBytes())
                        .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                if (sendMessageResult.getStatus().isSuccess())
                                    Toast.makeText(getApplication(), "Pair request sent!", Toast.LENGTH_SHORT).show();
                                else
                                    Toast.makeText(getApplication(), "Pair request failed!", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
        initializeServices();
        initializeDevice();

    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppForeground = true;

        // Check is Google Play Services available
        int connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext());

        if (connectionResult != ConnectionResult.SUCCESS) {
            // Google Play Services is NOT available. Show appropriate error dialog
            GooglePlayServicesUtil.showErrorDialogFragment(connectionResult, this, 0, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
        } else {
            apiClient.connect();
        }
    }

    @Override
    public void onStop() {
        // Unregister Node and Message listeners, disconnect GoogleApiClient and disable buttons
        Wearable.NodeApi.removeListener(apiClient, nodeListener);
        Wearable.MessageApi.removeListener(apiClient, messageListener);
        apiClient.disconnect();
        message1Button.setEnabled(false);
        isAppForeground = false;
        //message2Button.setEnabled(false);
        super.onStop();
    }

    public void sendMessage(final String message) {
        Log.d("myTag","Sending reply to "+remoteNodeId);
        Wearable.MessageApi.sendMessage(apiClient, remoteNodeId, MESSAGE1_PATH, message.getBytes())
                .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (sendMessageResult.getStatus().isSuccess())
                            Log.d("myTag","Message sent --> "+message);
                        else
                            Log.d("myTag","Message sending failed!");
                    }
                });
    }

    public void initializeServices() {
        // Create NodeListener that enables buttons when a node is connected and disables buttons when a node is disconnected
        nodeListener = new NodeApi.NodeListener() {
            @Override
            public void onPeerConnected(Node node) {
                remoteNodeId = node.getId();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(!isPaired)
                            message1Button.setEnabled(true);
                        Toast.makeText(getApplication(), getString(R.string.peer_connected), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onPeerDisconnected(Node node) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        message1Button.setEnabled(false);
                        //message2Button.setEnabled(false);
                        Toast.makeText(getApplication(), getString(R.string.peer_disconnected), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };

        // Create MessageListener that receives messages sent from a wearable
        messageListener = new MessageApi.MessageListener() {
            @Override
            public void onMessageReceived(final MessageEvent messageEvent) {
                if (messageEvent.getPath().equals(MESSAGE1_PATH)) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            final String message = new String(messageEvent.getData());
                            receivedMessagesText.append("\n" + message);
                            final Calendar cal = Calendar.getInstance();
                            String today = String.valueOf(cal.get(Calendar.YEAR))+"-"
                                    +String.valueOf(cal.get(Calendar.MONTH)+1)+"-"
                                    +String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
                            if(!isOperationInProgress) {
                                progress.show();
                                new LongOperation_getSchedule(
                                        "http://theinspirer.in/ilpscheduleapp/schedulelist_json.php",
                                        message, today).execute("");
                                isOperationInProgress = true;
                            }

                        }
                    });
                } else if (messageEvent.getPath().equals(MESSAGE2_PATH)) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            receivedMessagesText.append("\n" + getString(R.string.received_message2));
                        }
                    });
                }
            }
        };

        // Create GoogleApiClient
        apiClient = new GoogleApiClient.Builder(getApplicationContext()).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                // Register Node and Message listeners
                Wearable.NodeApi.addListener(apiClient, nodeListener);
                Wearable.MessageApi.addListener(apiClient, messageListener);
                // If there is a connected node, get it's id that is used when sending messages
                Wearable.NodeApi.getConnectedNodes(apiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        if (getConnectedNodesResult.getStatus().isSuccess() && getConnectedNodesResult.getNodes().size() > 0) {
                            remoteNodeId = getConnectedNodesResult.getNodes().get(0).getId();
                            if(!isPaired)
                                message1Button.setEnabled(true);
                        }
                    }
                });
            }

            @Override
            public void onConnectionSuspended(int i) {
                message1Button.setEnabled(false);
                //message2Button.setEnabled(false);
            }
        }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(ConnectionResult connectionResult) {
                if (connectionResult.getErrorCode() == ConnectionResult.API_UNAVAILABLE)
                    Toast.makeText(getApplicationContext(), getString(R.string.wearable_api_unavailable), Toast.LENGTH_LONG).show();
            }
        }).addApi(Wearable.API).build();

    }

    private void initializeDevice() {
        try {
            PackageManager m = getPackageManager();
            String s = getPackageName();
            PackageInfo p = m.getPackageInfo(s, 0);
            s = p.applicationInfo.dataDir;
            File pairFile = new File(s+"/pair");
            if(!pairFile.exists() && remoteNodeId!=null) {

                pairFile.createNewFile();
                FileWriter fw = new FileWriter(pairFile.getAbsoluteFile());
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(remoteNodeId);
                bw.close();
                Toast.makeText(MainActivity.this,"Paired with "+remoteNodeId,Toast.LENGTH_SHORT)
                        .show();
                message1Button.setEnabled(false);
                ((Button) message1Button).setText("Paired with wearable");
                isPaired = true;
            }
            else if (pairFile.exists()) {
                FileReader fr = new FileReader(pairFile);
                BufferedReader br = new BufferedReader(fr);
                remoteNodeId = br.readLine();
                message1Button.setEnabled(false);
                ((Button) message1Button).setText("Paired with wearable");
                isPaired = true;
            }

        } catch (PackageManager.NameNotFoundException ne) {
            ne.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                }catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            return "";
        }
        protected void onPostExecute(String unused) {
            // NOTE: You can call UI Element here.
            if (Error != null) {
                Toast.makeText( MainActivity.this,
                        "Error due to some network problem! Please connect to internet. ",
                        Toast.LENGTH_LONG).show();
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
                            sendMessage("success:"+course+":"+faculty+":"+slot+":"+room);
                        }
                        if (result.equalsIgnoreCase("success")) {
                            // do your success work
                        } else {
                            sendMessage("error:No schedule found!");
                        }
                    }
                    else {
                        Toast.makeText(MainActivity.this,
                                "Error due to some network problem! Please connect to internet. ",
                                Toast.LENGTH_LONG).show();
                        sendMessage("error:Network not available at device");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            isOperationInProgress = false;
            progress.hide();
        }
    }

}
