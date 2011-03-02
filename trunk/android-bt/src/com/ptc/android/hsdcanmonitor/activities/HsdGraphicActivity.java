package com.ptc.android.hsdcanmonitor.activities;

import java.util.ArrayList;

import com.ptc.android.hsdcanmonitor.CommandScheduler;
import com.ptc.android.hsdcanmonitor.CoreEngine;
import com.ptc.android.hsdcanmonitor.R;
import com.ptc.android.hsdcanmonitor.ResponseHandler;
import com.ptc.android.hsdcanmonitor.commands.GenericResponseDecoder;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class HsdGraphicActivity extends Activity {
    // Debugging
    private static final String TAG = "HsdCanMonitor.UI";
    private static final boolean D = true;

    private TextView mBattAmp; 

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, 
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );

        // Set up the window layout
        setContentView(R.layout.main);
        
        mBattAmp = (TextView) findViewById(R.id.hv_batt_amp);
    }

    @Override
    public void onStart() {
    	// else:
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");
    	if (CoreEngine.Exiting) {
    		finish();
    		return;
    	}
        // This is called each time we switch to this activity: set ourselves as
        // the current handler of all UI-related requests from the CoreEngine:
        CoreEngine.setCurrentHandler(mHandler);
        // Enforce that Background Monitoring is running:
    	if (!CommandScheduler.getInstance().isBackgroundMonitoring()) {
        	CommandScheduler.getInstance().startBackgroundCommands();
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	 return CoreEngine.onOptionsItemSelected(item);
    }

    // The Handler that gets information back from the underlying services
    private final Handler mHandler = new Handler() {
        @SuppressWarnings("unchecked")
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case CoreEngine.MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case CoreEngine.STATE_CONNECTED:
                	// Other info (device name) will be received in a separate message.
                    break;
               	case CoreEngine.STATE_CONNECTING:
                        Toast.makeText(getApplicationContext(), R.string.title_connecting,
                                Toast.LENGTH_SHORT).show();
                    break;
               	case CoreEngine.STATE_CONNECT_FAILED:
                    Toast.makeText(getApplicationContext(), R.string.title_connect_failed,
                            Toast.LENGTH_SHORT).show();
                break;
               	case CoreEngine.STATE_CONNECTION_LOST:
                    Toast.makeText(getApplicationContext(), R.string.title_connection_lost,
                            Toast.LENGTH_SHORT).show();
                break;
                case CoreEngine.STATE_NONE:
                    Toast.makeText(getApplicationContext(), R.string.title_not_connected,
                            Toast.LENGTH_SHORT).show();
                    break;
				}
                break;
            case CoreEngine.MESSAGE_DEVICE_NAME:
                String connectedDeviceName = msg.getData().getString(CoreEngine.DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + connectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case CoreEngine.MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getInt(CoreEngine.TOAST),
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
            	// Switch to console activity ?
            case CoreEngine.MESSAGE_SWITCH_UI:
                Intent liveIntent = new Intent(getApplicationContext(), HsdConsoleActivity.class);
                startActivity(liveIntent);
            	break;
            case CoreEngine.MESSAGE_FINISH:
                finishActivity(CoreEngine.REQUEST_CONNECT_DEVICE);
                finish();
            	break;
            case CoreEngine.MESSAGE_UI_UPDATE:
                ArrayList<Pair<Integer, String>> refreshValues = (ArrayList<Pair<Integer, String>>) msg.getData().get(CoreEngine.UI_UPDATE_ITEM);
                for (Pair<Integer, String> item : refreshValues) {
                	switch(item.first) {
                	case GenericResponseDecoder.BATT_AMP:
                		mBattAmp.setText(item.second);
                		break;
                	}
                	
                }
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
        // Unless we're logging to file, stop asking for values:
    	if (!ResponseHandler.getInstance().isLoggingEnabled()) {
        	CommandScheduler.getInstance().stopBackgroundCommands();
    	}
   }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
        // Unconditional stop of background commands:
    	CommandScheduler.getInstance().stopBackgroundCommands();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop everything: Don't do this:
        //CoreEngine.stopAllThreads();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
	
}
