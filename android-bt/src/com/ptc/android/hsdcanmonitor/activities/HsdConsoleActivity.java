package com.ptc.android.hsdcanmonitor.activities;

import com.ptc.android.hsdcanmonitor.CoreEngine;
import com.ptc.android.hsdcanmonitor.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class HsdConsoleActivity extends Activity {
    // Debugging
    private static final String TAG = "HsdCanMonitor.Console";
    private static final boolean D = CoreEngine.D;
    // Layout Views
    private TextView mTitle;
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, 
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
        setContentView(R.layout.main_console);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
        // Start the core engine:
        CoreEngine.setCurrentHandler(mHandler);
        CoreEngine.startInit();

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
            	sendManualCommand(view);
            }
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");
    	if (CoreEngine.Exiting) {
    		finish();
    		return;
    	}
        // This is called each time we switch to this activity: set ourselves as
        // the current handler of all UI-related requests from the CoreEngine:
        CoreEngine.setCurrentHandler(mHandler);
        
        // TODO? Check if connected to device and launch activity if needed...
    }

    
    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the command
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
            	sendManualCommand(view);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };
    
    private void sendManualCommand(TextView view) {
    	String cmd = view.getText().toString();
    	if (cmd.length() > 0) {
            CoreEngine.sendManualCommand(cmd);
            // Reset out string buffer to zero and clear the edit text field
            mOutEditText.setText("");
            mConversationArrayAdapter.add("Me:  " + cmd);
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
    	CoreEngine.prepareMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	return CoreEngine.onOptionsItemSelected(item);
   }

    // The Handler that gets information back from the underlying services
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case CoreEngine.MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case CoreEngine.STATE_CONNECTED:
                	// Other info (device name) will be received in a separate message.
                    mConversationArrayAdapter.clear();
                    break;
               	case CoreEngine.STATE_CONNECTING:
                    	mTitle.setText(R.string.title_connecting);
                    break;
               	case CoreEngine.STATE_CONNECT_FAILED:
                	mTitle.setText(R.string.title_connect_failed);
                break;
               	case CoreEngine.STATE_CONNECTION_LOST:
                	mTitle.setText(R.string.title_connection_lost);
                break;
                case CoreEngine.STATE_NONE:
                    mTitle.setText(R.string.msg_not_connected);
                    break;
				}
                break;
            case CoreEngine.MESSAGE_DEVICE_NAME:
                String connectedDeviceName = msg.getData().getString(CoreEngine.DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + connectedDeviceName, Toast.LENGTH_SHORT).show();
                mTitle.setText(R.string.msg_connected_to);
                mTitle.append(connectedDeviceName);
                break;
            case CoreEngine.MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getInt(CoreEngine.TOAST_MSG_ID),
                               Toast.LENGTH_SHORT).show();
                break;
            case CoreEngine.MESSAGE_REQUEST_BT:
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, CoreEngine.REQUEST_ENABLE_BT);
                break;
            case CoreEngine.MESSAGE_SCAN_DEVICES:
                Intent serverIntent = new Intent(getApplicationContext(), DeviceListActivity.class);
                startActivityForResult(serverIntent, CoreEngine.REQUEST_CONNECT_DEVICE);
                break;
            case CoreEngine.MESSAGE_COMMAND_RESPONSE:
                // We received a response to a manual command,
            	// let's display it on our console/chat screen:
            	mConversationArrayAdapter.add("Received in "+
            			msg.getData().getLong(CoreEngine.DURATION)+"ms: "+
            			msg.getData().getString(CoreEngine.RESPONSE));
            	break;
            case CoreEngine.MESSAGE_SWITCH_UI:
                Intent liveIntent = new Intent(getApplicationContext(), HsdLiveMonitoringActivity.class);
                startActivity(liveIntent);
            	break;
            case CoreEngine.MESSAGE_FINISH:
                //finishActivity(CoreEngine.REQUEST_CONNECT_DEVICE);
                finish();
            	break;
            }
        }
    };
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	CoreEngine.onActivityResult(requestCode, resultCode, data);
    }
    
    @Override
    protected synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop everything: Don't do this:
        //CoreEngine.stopAllThreads();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

}