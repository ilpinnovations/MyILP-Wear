package com.ilp.innovations.myilp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.ilp.innovations.myilp.beans.Session;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;


public class MainActivity extends Activity implements WearableListView.ClickListener{

    private static final int SPEECH_REQUEST_CODE = 0;

    List<Session> sessions = new ArrayList<Session>();
    private WearableListView listView;
    private Adapter adapter;
    private TextView date;
    private ProgressDialog progress;

    private final String MESSAGE1_PATH = "/message_path";
    private final String MESSAGE2_PATH = "/message2";

    private GoogleApiClient apiClient;
    private NodeApi.NodeListener nodeListener;
    private MessageApi.MessageListener messageListener;
    private String remoteNodeId;
    private Handler handler;

    private Intent serviceIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler();

        date = (TextView) findViewById(R.id.date);

        final Calendar cal = Calendar.getInstance();
        String today = String.valueOf(cal.get(Calendar.DAY_OF_MONTH))+"-"
                +String.valueOf(cal.get(Calendar.MONTH)+1)+"-"
                +String.valueOf(cal.get(Calendar.YEAR)+"\nSchedule");
        date.setText(today);



        progress = new ProgressDialog(this);
        progress.setIndeterminate(true);
        progress.setMessage("Waiting for schedule");

        listView = (WearableListView) findViewById(R.id.wearable_list);
        adapter = new Adapter(this, sessions);
        listView.setAdapter(adapter);
        listView.setClickListener(this);

        // Create NodeListener that enables buttons when a node is connected and disables buttons when a node is disconnected
        nodeListener = new NodeApi.NodeListener() {
            @Override
            public void onPeerConnected(Node node) {
                remoteNodeId = node.getId();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //message1Button.setEnabled(true);
                        //message2Button.setEnabled(true);
                    }
                });
                Toast.makeText(getApplication(), getString(R.string.peer_connected), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPeerDisconnected(Node node) {
                remoteNodeId = null;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //message1Button.setEnabled(false);
                        //message2Button.setEnabled(false);
                    }
                });
                Toast.makeText(getApplication(), getString(R.string.peer_disconnected), Toast.LENGTH_SHORT).show();
            }
        };

        // Create MessageListener that receives messages sent from a mobile
        messageListener = new MessageApi.MessageListener() {
            @Override
            public void onMessageReceived(final MessageEvent messageEvent) {
                Log.d("myTag","Received a message with path="+messageEvent.getPath()+
                        " and data="+messageEvent.getData());
                if (messageEvent.getPath().equals(MESSAGE1_PATH)) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            //receivedMessagesEditText.append("\n" + getString(R.string.received_message1));
                            final String message = new String(messageEvent.getData());
                            progress.hide();
                            if(message.equals("pair"))
                            {
                                sessions.clear();
                                adapter.notifyDataSetChanged();
                                boolean isPaired = pair();
                                if(isPaired){
                                    Toast.makeText(MainActivity.this,"Pairing successful",Toast.LENGTH_SHORT).show();
                                    sendMessage("paired");
                                }
                                else
                                    Toast.makeText(MainActivity.this,"Pairing failed",Toast.LENGTH_SHORT).show();
                            }
                            else {
                                String [] content = message.split(":");
                                    if(content[0].equalsIgnoreCase("success")) {
                                        Session session = new Session(content[1], content[2],
                                                "Slot:"+content[3],"Room:"+content[4]);
                                        sessions.add(session);
                                        Collections.sort(sessions);
                                        adapter.notifyDataSetChanged();
                                        //sendMessage("Success!");
                                    }
                                    else if(content[0].equalsIgnoreCase("error")) {
                                        sessions.clear();
                                        adapter.notifyDataSetChanged();
                                        createAlert(content[1], "error");
                                    }
                            }
                        }
                    });
                } else if (messageEvent.getPath().equals(MESSAGE2_PATH)) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            //receivedMessagesEditText.append("\n" + getString(R.string.received_message2));
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
                            //message1Button.setEnabled(true);
                            //message2Button.setEnabled(true);
                        }
                    }
                });
            }
            @Override
            public void onConnectionSuspended(int i) {
                //message1Button.setEnabled(false);
                //message2Button.setEnabled(false);
            }
        }).addApi(Wearable.API).build();

        //Initializes app with input
        initializeApp();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SEND);
        registerReceiver(receiver, filter);

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
    protected void onPause() {
        // Unregister Node and Message listeners, disconnect GoogleApiClient and disable buttons
        Wearable.NodeApi.removeListener(apiClient, nodeListener);
        Wearable.MessageApi.removeListener(apiClient, messageListener);
        apiClient.disconnect();
        //message1Button.setEnabled(false);
        //message2Button.setEnabled(false);
        super.onPause();
    }

    @Override
    public void onClick(WearableListView.ViewHolder viewHolder) {
        Integer tag = (Integer) viewHolder.itemView.getTag();
        // use this data to complete some action ...
        Toast.makeText(MainActivity.this,
                "Faculty:"+sessions.get(tag).getFaculty()+"\n"+sessions.get(tag).getTime()
                +"    "+sessions.get(tag).getRoom(),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTopEmptyRegionClick() {

    }

    private static final class Adapter extends WearableListView.Adapter {
        private List<Session> mDataset;
        private final Context mContext;
        private final LayoutInflater mInflater;

        // Provide a suitable constructor (depends on the kind of dataset)
        public Adapter(Context context, List<Session> dataset) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
            mDataset = dataset;
        }

        // Provide a reference to the type of views you're using
        public static class ItemViewHolder extends WearableListView.ViewHolder {
            private TextView sessionName;
            private TextView time;
            private TextView room;

            public ItemViewHolder(View itemView) {
                super(itemView);
                // find the text view within the custom item's layout
                sessionName = (TextView) itemView.findViewById(R.id.name);
                time = (TextView) itemView.findViewById(R.id.time);
                room = (TextView) itemView.findViewById(R.id.room);
            }
        }

        // Create new views for list items
        // (invoked by the WearableListView's layout manager)
        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                              int viewType) {
            // Inflate our custom layout for list items
            return new ItemViewHolder(mInflater.inflate(R.layout.list_item, null));
        }

        // Replace the contents of a list item
        // Instead of creating new views, the list tries to recycle existing ones
        // (invoked by the WearableListView's layout manager)
        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder,
                                     int position) {
            // retrieve the text view
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            TextView txt1 = itemHolder.sessionName;
            TextView txt3 = itemHolder.time;
            TextView txt4 = itemHolder.room;
            // replace text contents
            txt1.setText(mDataset.get(position).getSessionName());
            txt3.setText(mDataset.get(position).getTime());
            txt4.setText(mDataset.get(position).getRoom());
            // replace list item's metadata
            holder.itemView.setTag(position);
        }

        // Return the size of your dataset
        // (invoked by the WearableListView's layout manager)
        @Override
        public int getItemCount() {
            return mDataset.size();
        }
    }


    public void sendMessage(String message) {
        Wearable.MessageApi.sendMessage(apiClient, remoteNodeId, MESSAGE1_PATH, message.getBytes())
                .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (sendMessageResult.getStatus().isSuccess()) {
                            /*Toast.makeText(MainActivity.this, "Sent successfully", Toast.LENGTH_SHORT)
                                    .show();*/
                        } else {
                            Toast.makeText(MainActivity.this, "Sending failed", Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
    }

    private void initializeApp() {
        pair();
        progress.show();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    private boolean pair() {
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
                return true;
            }
            else if (pairFile.exists()) {
                FileReader fr = new FileReader(pairFile);
                BufferedReader br = new BufferedReader(fr);
                remoteNodeId = br.readLine();
                return false;
            }

        } catch (PackageManager.NameNotFoundException ne) {
            ne.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            Toast.makeText(MainActivity.this,"Searching for "+spokenText,Toast.LENGTH_SHORT).show();
            if(remoteNodeId!=null) {
                sendMessage(spokenText.toUpperCase().trim());
            }
            else {
                Toast.makeText(MainActivity.this,"No paired devices found!",Toast.LENGTH_SHORT).show();
                //finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void createAlert(String content, String type) {
        AlertDialog alertDialog = new AlertDialog.Builder(
                MainActivity.this).create();

        // Setting Dialog Title
        alertDialog.setTitle("Alert");

        // Setting Dialog Message
        alertDialog.setMessage(content);

        // Setting Icon to Dialog
        if(type.equals("error"))
            alertDialog.setIcon(R.drawable.fail);
        else if(type.equals("success"))
            alertDialog.setIcon(R.drawable.success);

        // Setting OK Button
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                initializeApp();
            }
        });
        alertDialog.getWindow().setLayout(300,250);
        // Showing Alert Message
        alertDialog.show();
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(Intent.ACTION_SEND)) {
                final String message = intent.getStringExtra("message");
                if(message.equals("pair"))
                {
                    sessions.clear();
                    adapter.notifyDataSetChanged();
                    boolean isPaired = pair();
                    if(isPaired){
                        Toast.makeText(MainActivity.this,"Pairing successful",Toast.LENGTH_SHORT).show();
                        sendMessage("paired");
                    }
                    else
                        Toast.makeText(MainActivity.this,"Pairing failed",Toast.LENGTH_SHORT).show();
                }
                else {
                    String [] content = message.split(":");
                    if(content[0].equalsIgnoreCase("success")) {
                        Session session = new Session(content[1], content[2],
                                "Slot:"+content[3],"Room:"+content[4]);
                        sessions.add(session);
                        Collections.sort(sessions);
                        adapter.notifyDataSetChanged();
                        //sendMessage("Success!");
                    }
                    else if(content[0].equalsIgnoreCase("error")) {
                        sessions.clear();
                        adapter.notifyDataSetChanged();
                        createAlert(content[1], "error");
                    }
                }
            }
        }
    };
}