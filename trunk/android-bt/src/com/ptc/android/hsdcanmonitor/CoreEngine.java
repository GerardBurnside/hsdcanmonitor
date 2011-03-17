package com.ptc.android.hsdcanmonitor;

import java.util.ArrayList;

import com.ptc.android.hsdcanmonitor.R;
import com.ptc.android.hsdcanmonitor.activities.DeviceListActivity;
import com.ptc.android.hsdcanmonitor.commands.CommandResponseObject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;


/**
 * This static class is the non graphical entry point of the application.
 * *
 * @author Guinness
 *
 */
public final class CoreEngine {
    // Debugging
    public static final String TAG = "HsdCanMonitor";
    public static final boolean D = true;
    // Intent request codes 
    public static final int REQUEST_CONNECT_DEVICE = 1;
    public static final int REQUEST_ENABLE_BT = 2;
    // Various states of the bluetooth connection:
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_CONNECT_FAILED = 3;
    public static final int STATE_CONNECTION_LOST = 4;
    // Message types sent to the activity Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_REQUEST_BT = 2;
    public static final int MESSAGE_FINISH = 3;
    public static final int MESSAGE_SCAN_DEVICES = 4;
    public static final int MESSAGE_DEVICE_NAME = 5;
    public static final int MESSAGE_TOAST = 6;
    public static final int MESSAGE_COMMAND_RESPONSE = 7;
    public static final int MESSAGE_UI_UPDATE = 8;
    public static final int MESSAGE_SWITCH_UI = 9;
    public static final int MESSAGE_SHOW_HV_DETAIL = 10;
    public static final int MESSAGE_TOAST_WITH_PARAM = 11;
    // Message keys:
    public static final String TOAST_MSG_ID = "toast_msg_id";
    public static final String TOAST_PARAM = "param";
    public static final String DEVICE_NAME = "dev_name";
    public static final String RESPONSE = "response";
    public static final String DURATION = "duration";
    public static final String UI_UPDATE_ITEM = "ui_update_item";
    public static final String CLASS_NAME = "class_name";
    // Local Bluetooth adapter
    private static BluetoothAdapter _bluetoothAdapter = null;
    private static volatile boolean _scanningDevices = false;

    // Parent Activity:
    protected static Handler _parentHandler;

    // Store references of threads so that we can stop them at will:
    private static Thread _interface;
    private static Thread _scheduler;
    private static Thread _responseHandler;
    
    // Flag to indicate all activities that the user requested to leave the application:
    public static volatile boolean Exiting = false;
    
	private CoreEngine() {} // static class - no instance !
	
	public static void startInit() {
        // Get local Bluetooth adapter
        _bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (_bluetoothAdapter == null) {
        	askForToastMessage(R.string.bt_not_enabled_leaving);
        	Message msg = _parentHandler.obtainMessage(MESSAGE_FINISH);
            _parentHandler.sendMessage(msg);
            return;
        }
        // If BT is not on, request that it be enabled.
        // init will resume upon onActivityResult
        if (!_bluetoothAdapter.isEnabled()) {
        	Message msg = _parentHandler.obtainMessage(MESSAGE_REQUEST_BT);
            _parentHandler.sendMessage(msg);
        } else {
            resumeInit();
        }
	}

	public static void resumeInit() {
		if (!CanInterface.getInstance().isConnected()) {
	        // Inform the UI that we are not connected yet:
	        setState(STATE_NONE);
			// Bluetooth is now up and running:
			scanDevices(); // TODO connect to last known device instead.
			// TODO Should try connecting to the previously connected device right away !
		}
	}
	
	public static synchronized void scanDevices() {
		if ((!_scanningDevices) && (!CanInterface.getInstance().isConnecting())) {
			_scanningDevices = true;
			Message msg = _parentHandler.obtainMessage(MESSAGE_SCAN_DEVICES);
	        _parentHandler.sendMessage(msg);
		}
	}
	
	public static void connectToDevice(final String address) {
        setState(STATE_CONNECTING);
        // Always cancel discovery because it will slow down a connection
        _bluetoothAdapter.cancelDiscovery();
		// Connect to the device in a separate Thread because a blocking call is made:
        new Thread() {
        	public void run() {
        		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);
        		if (CanInterface.getInstance().connectToDevice(device)) {
        			// True means success!
        			setState(STATE_CONNECTED);
        	    	Message msg = _parentHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        	        Bundle bundle = new Bundle();
        	        bundle.putString(DEVICE_NAME, device.getName());
        	        msg.setData(bundle);
        	    	_parentHandler.sendMessage(msg);

        		}
        		else setState(STATE_CONNECT_FAILED);
        		// In both cases:
        		_scanningDevices = false;
        	}
        }.start();
	}

    /**
     * Set the current state of the bluetooth connection
     * @param state  An integer defining the current connection state
     */
    synchronized static void setState(int state) {
        if (D) Log.d(TAG, "setState() -> " + state);
        // Give the new state to the Handler so the UI Activity can update
        _parentHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        switch (state) {
        case STATE_CONNECTED:
        	// We have just connected to the device,
        	// Let's first launch our commands-related threads:
        	startCommandResponseThreads();
        	// Then let's launch our initialization commands:
        	// TODO In fact we should have one set of init commands for console
        	// and one for polling.
            //if (D) Log.d(TAG, "Running Initialization commands...");
            //CommandScheduler.getInstance().sendInitCommands();
            break;
        case STATE_CONNECTION_LOST:
        case STATE_CONNECT_FAILED: // or only for LOST?
        	askForToastMessage(R.string.title_connect_failed);
        	// TODO? scanDevices();
            break;
        }
    }
    	
	private static void startCommandResponseThreads() {
        if (D) Log.d(TAG, "Launching BT commands threads...");
		if (_interface == null || !_interface.isAlive()) {
			_interface = new Thread(CanInterface.getInstance());
			_interface.start();
		}
		if (_scheduler == null || !_scheduler.isAlive()) {
			_scheduler = new Thread(CommandScheduler.getInstance());
			_scheduler.start();
		}
		if (_responseHandler == null || !_responseHandler.isAlive()) {
			_responseHandler = new Thread(ResponseHandler.getInstance());
			_responseHandler.start();
		}
	}
	
	public static void stopAllThreads() {
		// Request graceful stop:
		CanInterface.getInstance().stop();
		CommandScheduler.getInstance().stop();
		ResponseHandler.getInstance().stop();
		// Then interrupt the threads:
		if (_interface != null)
			_interface.interrupt();
		if (_scheduler != null)
			_scheduler.interrupt();
		if (_responseHandler != null)
			_responseHandler.interrupt();
		// Finally remove the references:
		_interface = null;
		_scheduler = null;
		_responseHandler = null;
	}
	
	/**
	 * This method sends UI data updates to the parent UI.
	 * @param res 
	 * 
	 * @param resource_id the id of the string in values/strings.xml
	 */
	public static void notifyUI(ArrayList<Pair<Integer, String>> res) {
    	Message msg = _parentHandler.obtainMessage(MESSAGE_UI_UPDATE);
        Bundle bundle = new Bundle();
        bundle.putSerializable(UI_UPDATE_ITEM, res);
        msg.setData(bundle);
    	_parentHandler.sendMessage(msg);
	}

	/**
	 * This method asks the parent UI to display a message.
	 * 
	 * @param resource_id the id of the string in values/strings.xml
	 */
	public static void askForToastMessage(int resource_id) {
    	Message msg = _parentHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putInt(TOAST_MSG_ID, resource_id);
        msg.setData(bundle);
    	_parentHandler.sendMessage(msg);
	}
	/**
	 * Same as previous but with an additional text param.
	 * 
	 * @param resource_id the id of the string in values/strings.xml
	 * @param additionalString to be concatenated to the message.
	 */
	public static void askForToastMessageWithParam(int resource_id, String param) {
    	Message msg = _parentHandler.obtainMessage(MESSAGE_TOAST_WITH_PARAM);
        Bundle bundle = new Bundle();
        bundle.putInt(TOAST_MSG_ID, resource_id);
        bundle.putString(TOAST_PARAM, param);
        msg.setData(bundle);
    	_parentHandler.sendMessage(msg);
	}

    public static void sendManualCommand(final String cmdStr) {
        // Check that we're actually connected before trying anything
        if (!CanInterface.getInstance().isConnected()) {
        	askForToastMessage(R.string.not_connected);
            return;
        }

        // Check that there's actually something to send
        if (cmdStr.length() > 0) {
            // Get the message bytes and tell the scheduler to send the cmd
        	final CommandResponseObject cmd = new CommandResponseObject(cmdStr);
        	cmd.setSender(cmd); // Any Object does the trick!
            CommandScheduler.getInstance().addManualCommand(cmd);
            // Start a thread to listen to this specific response:
            new Thread() {
            	public void run() {
            		synchronized (cmd) {
						try {
							String resultStr = cmd.getResponseString();
							if ("".equals(resultStr)) {
						        if (D) Log.d(TAG, "Waiting for response of cmd: " + cmdStr);
								cmd.wait();
								// Now this should no longer be null:
								resultStr = cmd.getResponseString();
							}
							// If we're here, it means we've been woken up by
							// the reception of the response:
							// (or that we had received the reponse before waiting!)
					        if (D) Log.d(TAG, "Response of cmd (" + cmdStr + ") received: " + resultStr);
							// Let's notify the UI:
					    	Message msg = _parentHandler.obtainMessage(MESSAGE_COMMAND_RESPONSE);
					        Bundle bundle = new Bundle();
					        bundle.putLong(DURATION, cmd.getDuration());
					        bundle.putString(RESPONSE, resultStr);
					        msg.setData(bundle);
					    	_parentHandler.sendMessage(msg);
						} catch (InterruptedException e) {
							if (D) Log.e(TAG, "Interrupted while waiting for command response! :"+cmdStr);
						}
					}
            	}
            }.start();
        }
    }
    /**
     * The handler is the currently displayed activity.
     * @param handler
     */
	public static void setCurrentHandler(Handler handler) {
		_parentHandler = handler;
		//if (_parentHandler == null) {
			// An activity just ceased, let's check if 
			// another one is started within two seconds,
			// otherwise let's kill all threads?
			// TODO?
			// Or not?: example: phone call while driving...
		//}
	}
	
	/**
	 * Menu shared between activities:
	 */
	public static void prepareMenu(Menu menu) {
    	// Change the log to file menu label if needed:
        MenuItem item = menu.findItem(R.id.monitoring_on_off);
    	if (ResponseHandler.getInstance().isLoggingEnabled()) {
    		// Change the label of the menu:
    		item.setTitle(R.string.stop_logging);
    	}
    	else {
    		// Change the label of the menu:
    		item.setTitle(R.string.log_to_file);
    	}
	}
	
	/**
	 * Below are helper methods to share code between the activities
	 */
	public static boolean onOptionsItemSelected(MenuItem item) {
		Message msg;
        switch (item.getItemId()) {
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
        	// DO not call scanDevices() because we're bypassing checks!
			_scanningDevices = true;
			msg = _parentHandler.obtainMessage(MESSAGE_SCAN_DEVICES);
	        _parentHandler.sendMessage(msg);
            return true;
        case R.id.reverse_beep_remove:
        	sendManualCommand("AT SH 7C0");
        	sendManualCommand("3bac40");
        	// TODO: check the response !
            return true;
        case R.id.reverse_beep_restore:
        	sendManualCommand("AT SH 7C0");
        	sendManualCommand("3bac00");
            return true;

        case R.id.seatbelt_beep_restore_default:
        	sendManualCommand("AT SH 7C0");
        	sendManualCommand("3ba7c0");
        	// TODO: check the response !
            return true;
        case R.id.seatbelt_beep_driver_only:
        	sendManualCommand("AT SH 7C0");
        	sendManualCommand("3ba740");
        	// TODO: check the response !
            return true;
        case R.id.seatbelt_beep_remove_all:
        	sendManualCommand("AT SH 7C0");
        	sendManualCommand("3ba700");
        	// TODO: check the response !
            return true;
        case R.id.monitoring_on_off:
        	// Maybe the "settings" menu would suit this action better (with a tick box)..
        	// Start or stop monitoring in a file on the SD card.
        	ResponseHandler.getInstance().toggleLogging();
            return true;
	    case R.id.app_settings:
	    	// TODO?
	    	// TEST: For now, show HV DETAIL:
			msg = _parentHandler.obtainMessage(MESSAGE_SHOW_HV_DETAIL);
	        _parentHandler.sendMessage(msg);
	    	
	        return true;
	    case R.id.switch_view:
			msg = _parentHandler.obtainMessage(MESSAGE_SWITCH_UI);
	        _parentHandler.sendMessage(msg);
	        return true;
	    case R.id.exit:
	    	/*** Disabled because DeviceListActivity crashes on subsequent start:*/
	    	// Let all Activities know that they should not be displayed:
	    	Exiting = true;
	    	stopAllThreads();
	    	msg = _parentHandler.obtainMessage(MESSAGE_FINISH);
	    	_parentHandler.sendMessage(msg);
	    	// Because Android will not let us die in peace,
	    	// let's allow the activities to be created again later:
	    	new Thread() {
	    		@Override
	    		public void run() {
	    			try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    			Exiting = false;
	    		}
	    	}.start();
	        return true;
        }
        return false;
	}

	public static void onActivityResult(int requestCode, int resultCode,
			Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BluetoothDevice object:
                connectToDevice(address);
            }
            else if (resultCode == Activity.RESULT_CANCELED) {
            	if (!CanInterface.getInstance().isConnected()) {
            		// The user probably wants to leave the app! Let him!
            		Message msg = _parentHandler.obtainMessage(MESSAGE_FINISH);
        	    	_parentHandler.sendMessage(msg);
            	}
            }
            _scanningDevices = false;
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled:
            	resumeInit();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                askForToastMessage(R.string.bt_not_enabled_leaving);
            	Message msg = _parentHandler.obtainMessage(MESSAGE_FINISH);
            	_parentHandler.sendMessage(msg);       	
            }
        }
	}

}
