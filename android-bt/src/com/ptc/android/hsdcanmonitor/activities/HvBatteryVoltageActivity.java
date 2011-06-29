package com.ptc.android.hsdcanmonitor.activities;


import java.util.ArrayList;

import com.ptc.android.hsdcanmonitor.CanInterface;
import com.ptc.android.hsdcanmonitor.CommandScheduler;
import com.ptc.android.hsdcanmonitor.CoreEngine;
import com.ptc.android.hsdcanmonitor.R;
import com.ptc.android.hsdcanmonitor.ResponseHandler;
import com.ptc.android.hsdcanmonitor.commands.GenericResponseDecoder;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
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

public class HvBatteryVoltageActivity extends Activity {
    // Debugging
    private static final String TAG = "HsdCanMonitor.HV";
    private static final boolean D = CoreEngine.D;

    private static final float MAX_VOLTAGE_DISCREPANCY = 0.25f; // Should be configurable!
    private static final int MAX_CONSECUTIVE_DISCREPANCIES = 5; // Until I figure out a better way ;-)
    private int discrepancies_count = 0;
    private static volatile boolean _visible = false;
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
    private TextView mAuxBatt;
    
	protected AlertDialog.Builder alertBuilder;

    
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
        mAuxBatt = (TextView) findViewById(R.id.aux_batt);
        
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
    	_visible = true;
    	alertBuilder = new AlertDialog.Builder(this);
    	// This is called each time we switch to this activity: set ourselves as
        // the current handler of all UI-related requests from the CoreEngine:
        CoreEngine.setCurrentHandler(mHandler);

        // TODO: Should load the XML corresponding to HSD type (PII or PIII):
        CommandScheduler.getInstance().setMonitoringCommands("hv_batt_PIII.xml");
        
        if (CanInterface.getInstance().isConnected()) {
            // Enforce that Background Monitoring is running:
        	if (!CommandScheduler.getInstance().isBackgroundMonitoring()) {
            	CommandScheduler.getInstance().startLiveMonitoringCommands(false); // Bypass init commands
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
    	private float[] cellPairs = new float[15]; // Unused first slot!
    	private AlertDialog alertVoltageDiscrepancy = null;
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
                break;
               	case CoreEngine.STATE_CONNECTION_LOST:
                    Toast.makeText(getApplicationContext(), R.string.title_connection_lost,
                            Toast.LENGTH_SHORT).show();
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
            case CoreEngine.MESSAGE_REQUEST_BT:
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, CoreEngine.REQUEST_ENABLE_BT);
                break;
            case CoreEngine.MESSAGE_SCAN_DEVICES:
                Intent serverIntent = new Intent(getApplicationContext(), DeviceListActivity.class);
                startActivityForResult(serverIntent, CoreEngine.REQUEST_CONNECT_DEVICE);
                break;
            case CoreEngine.MESSAGE_COMMAND_RESPONSE:
            	break;
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
                	case GenericResponseDecoder.HV_BATT_VOLT_01:
                		if (mGroup01 != null) {
                			mGroup01.setText(item.second);
                		}
                		cellPairs[1] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_02:
                		if (mGroup02 != null) {
                			mGroup02.setText(item.second);
                		}                			
                		cellPairs[2] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_03:
                		if (mGroup03 != null) {
                			mGroup03.setText(item.second);
                		}                			
                		cellPairs[3] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_04:
                		if (mGroup04 != null) {
                			mGroup04.setText(item.second);
                		}                			
                		cellPairs[4] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_05:
                		if (mGroup05 != null) {
                			mGroup05.setText(item.second);
                		}                			
                		cellPairs[6] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_06:
                		if (mGroup06 != null) {
                			mGroup06.setText(item.second);
                		}                			
                		cellPairs[6] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_07:
                		if (mGroup07 != null) {
                			mGroup07.setText(item.second);
                		}                			
                		cellPairs[7] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_08:
                		if (mGroup08 != null) {
                			mGroup08.setText(item.second);
                		}                			
                		cellPairs[8] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_09:
                		if (mGroup09 != null) {
                			mGroup09.setText(item.second);
                		}                			
                		cellPairs[9] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_10:
                		if (mGroup10 != null) {
                			mGroup10.setText(item.second);
                		}                			
                		cellPairs[10] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_11:
                		if (mGroup11 != null) {
                			mGroup11.setText(item.second);
                		}                			
                		cellPairs[11] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_12:
                		if (mGroup12 != null) {
                			mGroup12.setText(item.second);
                		}                			
                		cellPairs[12] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_13:
                		if (mGroup13 != null) {
                			mGroup13.setText(item.second);
                		}                			
                		cellPairs[13] = Float.parseFloat(item.second);
                		break;
                	case GenericResponseDecoder.HV_BATT_VOLT_14:
                		if (mGroup14 != null) {
                			mGroup14.setText(item.second);
                		} // All 14 groups have been received, let's run a quick check:
                		cellPairs[14] = Float.parseFloat(item.second);
                		checkVoltageDiscrepancies();
                		break;
                	case GenericResponseDecoder.AUX_BATT:
                		if (mAuxBatt != null) {
                			mAuxBatt.setText(item.second);
                		}                			
                		break;
                	}
                }
            }
        }

		private void checkVoltageDiscrepancies() {
			float minVoltageValue = 16; // Nominal voltage of a cell pair should be 14.4V with no load.
			float maxVoltageValue = 0;
			for (float val : cellPairs) {
				if (val == 0) // Ignore empty values!
					continue;
				if (val < minVoltageValue)
					minVoltageValue = val;
				if (val > maxVoltageValue)
					maxVoltageValue = val;
			}
			float discrepancy = maxVoltageValue - minVoltageValue;
			if (discrepancy > MAX_VOLTAGE_DISCREPANCY) {
				if (++discrepancies_count > MAX_CONSECUTIVE_DISCREPANCIES) {
					if (alertVoltageDiscrepancy == null || !alertVoltageDiscrepancy.isShowing()) {
						alertBuilder.setMessage("WARNING: Voltage difference of "
		                        +discrepancy+ "V detected for "+MAX_CONSECUTIVE_DISCREPANCIES+" measures!!!")
						       .setCancelable(false)
						       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
						           public void onClick(DialogInterface dialog, int id) {
						                dialog.cancel();
						                alertVoltageDiscrepancy = null;
						                // Reset the counter
						                discrepancies_count = 0;
						           }
						       });
						alertVoltageDiscrepancy = alertBuilder.create();
						alertVoltageDiscrepancy.show();
					}
				}
				// else: ignore for now.
			}
			else { // Reset the counter
				discrepancies_count = 0;
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
    	//if (!ResponseHandler.getInstance().isLoggingEnabled()) {
        //	CommandScheduler.getInstance().stopLiveMonitoringCommands();
    	//}
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
        // Stop everything: Don't do this:
        //CoreEngine.stopAllThreads();
        // Unconditional stop of background commands:
    	CommandScheduler.getInstance().stopLiveMonitoringCommands(mHandler);
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }
	
}