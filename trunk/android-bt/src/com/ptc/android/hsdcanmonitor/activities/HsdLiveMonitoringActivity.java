package com.ptc.android.hsdcanmonitor.activities;

import java.util.ArrayList;

import com.ptc.android.hsdcanmonitor.CanInterface;
import com.ptc.android.hsdcanmonitor.CommandScheduler;
import com.ptc.android.hsdcanmonitor.CoreEngine;
import com.ptc.android.hsdcanmonitor.R;
import com.ptc.android.hsdcanmonitor.ResponseHandler;
import com.ptc.android.hsdcanmonitor.commands.GenericResponseDecoder;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Color;
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

public class HsdLiveMonitoringActivity extends Activity {
    // Debugging
    private static final String TAG = "HsdCanMonitor.UI";
    private static final boolean D = CoreEngine.D;

    private TextView mBattAmp; 
    private TextView mIceTemp;
    private TextView mIceTorque;
    private TextView mMG1RPM;
    private TextView mMG2RPM;
    private TextView mIceRPM;
    private static volatile boolean _visible = false;

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
        
        // Any one of those may be null depending on the layout:
        mBattAmp = (TextView) findViewById(R.id.hv_batt_amp);
        mIceRPM = (TextView) findViewById(R.id.ice_rpm);
        mIceTemp = (TextView) findViewById(R.id.ice_temp);
        mMG1RPM = (TextView) findViewById(R.id.mg1_rpm);
        mMG2RPM = (TextView) findViewById(R.id.mg2_rpm);
        mIceTorque = (TextView) findViewById(R.id.ice_torque);

        CoreEngine.setCurrentHandler(mHandler);
        CoreEngine.startInit();
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
        _visible = true;
        // This is called each time we switch to this activity: set ourselves as
        // the current handler of all UI-related requests from the CoreEngine:
        CoreEngine.setCurrentHandler(mHandler);
        if (CanInterface.getInstance().isConnected()) {
            // Enforce that Background Monitoring is running:
        	if (!CommandScheduler.getInstance().isBackgroundMonitoring()) {
            	CommandScheduler.getInstance().startLiveMonitoringCommands(true);
        	}
        }
        else {
        	// Let's wait briefly then launch the BT discovery if needed:
        	new Thread() {
        		public void run() {
        			try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// Ignore
						if (D) Log.e(TAG, "Interrupted while waiting before BT discovery...");
					}
                	if (_visible && !CanInterface.getInstance().isConnecting())
                		CoreEngine.scanDevices();
        		}
        	}.start();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        CoreEngine.prepareMenu(menu);
        return super.onPrepareOptionsMenu(menu);
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
                    // Enforce that Background Monitoring is running:
                	if (!CommandScheduler.getInstance().isBackgroundMonitoring()) {
                    	CommandScheduler.getInstance().startLiveMonitoringCommands(true);
                	}
                    break;
               	case CoreEngine.STATE_CONNECTING:
                        Toast.makeText(getApplicationContext(), R.string.title_connecting,
                                Toast.LENGTH_SHORT).show();
                    break;
               	case CoreEngine.STATE_CONNECT_FAILED:
                    Toast.makeText(getApplicationContext(), R.string.title_connect_failed,
                            Toast.LENGTH_SHORT).show();
                    CoreEngine.scanDevices();
                break;
               	case CoreEngine.STATE_CONNECTION_LOST:
                    Toast.makeText(getApplicationContext(), R.string.title_connection_lost,
                            Toast.LENGTH_SHORT).show();
                    CoreEngine.scanDevices();
                break;
                case CoreEngine.STATE_NONE:
                    Toast.makeText(getApplicationContext(), R.string.msg_not_connected,
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
                Toast.makeText(getApplicationContext(), msg.getData().getInt(CoreEngine.TOAST_MSG_ID),
                               Toast.LENGTH_SHORT).show();
                break;
            case CoreEngine.MESSAGE_TOAST_WITH_PARAM:
                String param = msg.getData().getString(CoreEngine.TOAST_PARAM);
                CharSequence txt = getText(msg.getData().getInt(CoreEngine.TOAST_MSG_ID));
                Toast.makeText(getApplicationContext(), txt + param,
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
                //finishActivity(CoreEngine.REQUEST_CONNECT_DEVICE);
                finish();
            	break;
            case CoreEngine.MESSAGE_SHOW_HV_DETAIL:
                Intent hvIntent = new Intent(getApplicationContext(), HvBatteryVoltageActivity.class);
                startActivity(hvIntent);
                break;
            case CoreEngine.MESSAGE_UI_UPDATE:
                ArrayList<Pair<Integer, String>> refreshValues = (ArrayList<Pair<Integer, String>>) msg.getData().get(CoreEngine.UI_UPDATE_ITEM);
                for (Pair<Integer, String> item : refreshValues) {
                	switch(item.first) {
                	case GenericResponseDecoder.BATT_AMP:
                		if (mBattAmp != null) {
                			mBattAmp.setText(item.second);
                			if (item.second.charAt(0) == '-') {
                				// Cool, we're getting energy back
                				mBattAmp.setTextColor(Color.GREEN);
                			}
                			else mBattAmp.setTextColor(Color.LTGRAY);
                		}                			
                		break;
                	case GenericResponseDecoder.ICE_TEMP:
                		if (mIceTemp != null) {
                			mIceTemp.setText(item.second);
                			final String IceWontTurnOff = "40";
                			final String NeedIdlingCheckCeremony = "70";
                   			final String howHotIsTooHot = "100";
                			if (IceWontTurnOff.length() >= item.second.length() 
                				&& IceWontTurnOff.compareTo(item.second) > 0) {
                				mIceTemp.setTextColor(Color.BLUE);                				
                			}
                			else if (NeedIdlingCheckCeremony.length() == item.second.length() 
                    				&& NeedIdlingCheckCeremony.compareTo(item.second) > 0) {
                				mIceTemp.setTextColor(Color.GRAY);
                			}
                			else if (howHotIsTooHot.length() > item.second.length()) {
                				mIceTemp.setTextColor(Color.GREEN);
                			}
                			else mIceTemp.setTextColor(Color.RED);
                		}
                		break;
                	case GenericResponseDecoder.ICE_RPM:
                		if (mIceRPM != null) {
                			mIceRPM.setText(item.second);
                			// Lowest rpm I've seen that will cause fuel injection if I go to N
                			final String noFuelRPM = "925";
                			if (noFuelRPM.length() >= item.second.length() 
                				&& noFuelRPM.compareTo(item.second) > 0) {
                				mIceRPM.setTextColor(Color.DKGRAY);                				
                			}
                			else {
                				mIceRPM.setTextColor(Color.WHITE);
                			}
                		}
                		break;
                	case GenericResponseDecoder.ICE_TORQUE:
                		if (mIceTorque != null)
                			mIceTorque.setText(item.second);
                		break;
                	case GenericResponseDecoder.MG1_RPM:
                		if (mMG1RPM != null)
                			mMG1RPM.setText(item.second);
                		break;
                	case GenericResponseDecoder.MG2_RPM:
                		if (mMG2RPM != null)
                			mMG2RPM.setText(item.second);
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
   }

    @Override
    protected synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "- ON RESUME -");
   }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
        _visible = false;
        // Unless we're logging to file, stop asking for values:
    	if (!ResponseHandler.getInstance().isLoggingEnabled()) {
        	CommandScheduler.getInstance().stopLiveMonitoringCommands();
    	}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unconditional stop of background commands:
    	CommandScheduler.getInstance().stopLiveMonitoringCommands();
        // Stop everything:
        //CoreEngine.stopAllThreads(); // Don't do this for now?
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
	
}
