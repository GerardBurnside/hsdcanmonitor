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
import android.content.SharedPreferences;
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

    private TextView mBattKW; 
    private TextView mBattSOC; 
    private TextView mIceTemp;
    private TextView mIceTorque;
    private TextView mMG1RPM;
    private TextView mMG2RPM;
    private TextView mIceRPM;
    //private TextView mVehicleLoad;
    private TextView mInverterTempMGx;
    private TextView mDC_Cnv_Temp;
    private TextView mFuelTank;
    private static volatile boolean _visible = false;
    private int MAX_UNSUCCESSFUL_ATTEMPTS_FROM_SETTINGS = 15;

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
        
        // TODO: Only ask the commands for items that are displayed:
        // This requires re-writing each command and deciding how to group them
        // for best efficiency...
        
        // Any one of those may be null depending on the layout:
        mBattKW = (TextView) findViewById(R.id.hv_batt_kw);
        mBattSOC = (TextView) findViewById(R.id.hv_batt_soc);
        mIceRPM = (TextView) findViewById(R.id.ice_rpm);
        mIceTemp = (TextView) findViewById(R.id.ice_temp);
        mMG1RPM = (TextView) findViewById(R.id.mg1_rpm);
        mMG2RPM = (TextView) findViewById(R.id.mg2_rpm);
        mIceTorque = (TextView) findViewById(R.id.ice_torque);
        //mVehicleLoad = (TextView) findViewById(R.id.vehicle_load);
        mInverterTempMGx = (TextView) findViewById(R.id.inverter_temp_mgx);
        mDC_Cnv_Temp = (TextView) findViewById(R.id.dc_cnv_temp);
        mFuelTank = (TextView) findViewById(R.id.fuel_tank);

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
        	deferredScanDevices();
        }
    }
    
    private void deferredScanDevices() {
    	// Let's wait briefly then launch the BT discovery if needed:
    	new Thread() {
    		public void run() {
    			try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// Ignore
					if (D) Log.e(TAG, "Interrupted while waiting before BT discovery...");
				}
            	if (_visible && !CanInterface.getInstance().isConnecting()) {
            		CoreEngine.scanDevices();
            	}
            		
    		}
    	}.start();
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
                    if (--MAX_UNSUCCESSFUL_ATTEMPTS_FROM_SETTINGS > 0) {
                    	deferredScanDevices();
                    }
                    // else: let the user press the menu himself (TODO: print a Toast?)
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
                // Remember the address of the last successful connection:
                setPreferredDeviceAddress(msg.getData().getString(CoreEngine.DEVICE_ADDRESS));
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
            case CoreEngine.MESSAGE_SCAN_DEVICES_MANUAL:
                // Reset the device stored in memory to avoid infinite loop with stored value:
               	setPreferredDeviceAddress(null);
               	// no break: fall through:
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
                	case GenericResponseDecoder.BATT_KW:
                		if (mBattKW != null) {
                			mBattKW.setText(item.second);
                			if (item.second.charAt(0) == '-') {
                				// Cool, we're getting energy back
                				mBattKW.setTextColor(Color.GREEN);
                			}
                			else mBattKW.setTextColor(Color.LTGRAY);
                		}                			
                		break;
                	case GenericResponseDecoder.STATE_OF_CHARGE:
                		if (mBattSOC != null) {
                			mBattSOC.setText(item.second); // TODO: Colors?
                		}                			
                		break;
                	case GenericResponseDecoder.ICE_TEMP:
                		if (mIceTemp != null) {
                			mIceTemp.setText(item.second);
                			final String IceWontTurnOff = "40";
                			final String NeedIdlingCheckCeremony = "71";
                   			final String howHotIsTooHot = "98";
                   			if ((item.second.charAt(0) == '-')
                   					|| (IceWontTurnOff.length() >= item.second.length() 
                				&& (IceWontTurnOff.compareTo(item.second)) > 0)) {
                				mIceTemp.setTextColor(Color.BLUE);		
                			}
                			else if (NeedIdlingCheckCeremony.length() == item.second.length() 
                    				&& NeedIdlingCheckCeremony.compareTo(item.second) > 0) {
                				mIceTemp.setTextColor(Color.GRAY);
                			}
                			else if (howHotIsTooHot.length() >= item.second.length()
                						&& howHotIsTooHot.compareTo(item.second) > 0) {
                				mIceTemp.setTextColor(Color.GREEN);
                			}
                			else mIceTemp.setTextColor(Color.RED);
                		}
                		break;
                	case GenericResponseDecoder.ICE_RPM:
                		if (mIceRPM != null) {
                			mIceRPM.setText(item.second);
                			// Lowest rpm I've seen that can still cause fuel injection when switching to N
                			final String noFuelRPM = "900";
                			final String lessEfficientRPM = "3300";
                			if (noFuelRPM.length() >= item.second.length() 
                					&& noFuelRPM.compareTo(item.second) > 0) {
                				mIceRPM.setTextColor(Color.DKGRAY);
                			}
                			else if (lessEfficientRPM.length() > item.second.length() 
                    				|| lessEfficientRPM.compareTo(item.second) > 0) {
                				mIceRPM.setTextColor(Color.WHITE);
                			}
                			else {
                				mIceRPM.setTextColor(Color.RED);
                			}
                		}
                		break;
                	case GenericResponseDecoder.ICE_TORQUE:
                		if (mIceTorque != null) {
                			final int length = item.second.length();
                			if (length > 3) // Probably a mistake as Torque never exceeds 150 NM
                				break; // Ignore.
                			// Else:
                		    mIceTorque.setText(item.second);
                			// Values where torque is supposedly more efficient:
                			final String torqueTooLow = "72";
                			final String torqueTooHigh = "115";
                			if ((item.second.charAt(0) == '-')
                   					|| (torqueTooLow.length() >= length
                    				&& torqueTooLow.compareTo(item.second) > 0)) {
                				mIceTorque.setTextColor(Color.GRAY);
                    		}
                    		else if (torqueTooHigh.length() > length 
                        			|| torqueTooHigh.compareTo(item.second) > 0) {
                    			mIceTorque.setTextColor(Color.GREEN);
                    		}
                    		else {
                    			mIceTorque.setTextColor(Color.RED);
                    		}
                		}
                		break;
                	case GenericResponseDecoder.MG1_RPM:
                		if (mMG1RPM != null)
                			mMG1RPM.setText(item.second);
                		break;
                	case GenericResponseDecoder.MG2_RPM:
                		if (mMG2RPM != null)
                			mMG2RPM.setText(item.second);
                		break;
	            	/*case GenericResponseDecoder.VEHICLE_LOAD:
	            		if (mVehicleLoad != null)
	            			mVehicleLoad.setText(item.second);
	            		break;*/
                	case GenericResponseDecoder.INVERTER_TEMP_MGx:
                		if (mInverterTempMGx != null)
                			mInverterTempMGx.setText(item.second);
                		break;
                	case GenericResponseDecoder.DC_CNV_TEMP:
                		if (mDC_Cnv_Temp != null)
                			mDC_Cnv_Temp.setText(item.second);
                		break;
                	case GenericResponseDecoder.FUEL_TANK:
                		if (mFuelTank != null)
                			mFuelTank.setText(item.second);
                		break;
	            	}
                }
                break;
            }
        }
    };
    
    public void setPreferredDeviceAddress(String addr) {
		SharedPreferences.Editor settings = getSharedPreferences(CoreEngine.PREFS_NAME, MODE_PRIVATE).edit();
		settings.putString(CoreEngine.DEVICE_ADDRESS, addr);
		settings.commit();
    }
    
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
        	CommandScheduler.getInstance().stopLiveMonitoringCommands(mHandler);
    	}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unconditional stop of background commands:
    	CommandScheduler.getInstance().stopLiveMonitoringCommands(mHandler);
        // Stop everything:
        //CoreEngine.stopAllThreads(); // Don't do this for now?
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
	
}
