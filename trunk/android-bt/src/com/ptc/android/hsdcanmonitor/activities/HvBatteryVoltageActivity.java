package com.ptc.android.hsdcanmonitor.activities;


import com.ptc.android.hsdcanmonitor.CommandScheduler;
import com.ptc.android.hsdcanmonitor.CoreEngine;
import com.ptc.android.hsdcanmonitor.R;
import com.ptc.android.hsdcanmonitor.ResponseHandler;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class HvBatteryVoltageActivity extends Activity {
    // Debugging
    private static final String TAG = "HsdCanMonitor.HV";
    private static final boolean D = CoreEngine.D;
    // Layout Views
    private TextView mGroup01;
    private TextView mGroup02;
    private TextView mGroup03;
    private TextView mGroup04;
    private TextView mGroup05;
    private TextView mGroup06;
    private TextView mGroup07;
    private TextView mGroup08;
    private TextView mGroup09;
    private TextView mGroup10;
    private TextView mGroup11;
    private TextView mGroup12;
    private TextView mGroup13;
    private TextView mGroup14;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, 
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON );
        setContentView(R.layout.hv_batt_cells);

        // Find the layout items:
        mGroup01 = (TextView) findViewById(R.id.hv_volt_group01);
        mGroup02 = (TextView) findViewById(R.id.hv_volt_group02);
        mGroup03 = (TextView) findViewById(R.id.hv_volt_group03);
        mGroup04 = (TextView) findViewById(R.id.hv_volt_group04);
        mGroup05 = (TextView) findViewById(R.id.hv_volt_group05);
        mGroup06 = (TextView) findViewById(R.id.hv_volt_group06);
        mGroup07 = (TextView) findViewById(R.id.hv_volt_group07);
        mGroup08 = (TextView) findViewById(R.id.hv_volt_group08);
        mGroup09 = (TextView) findViewById(R.id.hv_volt_group09);
        mGroup10 = (TextView) findViewById(R.id.hv_volt_group10);
        mGroup11 = (TextView) findViewById(R.id.hv_volt_group11);
        mGroup12 = (TextView) findViewById(R.id.hv_volt_group12);
        mGroup13 = (TextView) findViewById(R.id.hv_volt_group13);
        mGroup14 = (TextView) findViewById(R.id.hv_volt_group14);
        
        // Start the core engine:
        CoreEngine.setCurrentHandler(mHandler);
        //CoreEngine.startInit();

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
        
        // TODO: Check if connected to device and launch activity if needed...
        
        // TODO: Keep asking for: 
        /*
         * 7E2 21 81
14 couples d'octets consécutifs
à diviser par 1000 (coef à vérifier) pour obtenir des volts.
         */
    }
    
    private void sendManualCommand(TextView view) {
    	String cmd = view.getText().toString();
    	if (cmd.length() > 0) {
            CoreEngine.sendManualCommand(cmd);
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
            	// Interpret the response here:
            	// TODO:

            	break;
            case CoreEngine.MESSAGE_SWITCH_UI:
                Intent liveIntent = new Intent(getApplicationContext(), HsdGraphicActivity.class);
                startActivity(liveIntent);
            	break;
            case CoreEngine.MESSAGE_FINISH:
                finishActivity(CoreEngine.REQUEST_CONNECT_DEVICE);
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