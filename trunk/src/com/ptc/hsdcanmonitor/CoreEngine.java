package com.ptc.hsdcanmonitor;

import com.ptc.android.hsdcanmonitor.R;
import com.ptc.hsdcanmonitor.commands.CommandResponseObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


/**
 * This class is the non graphical entry point of the application.
 *
 * @author Guinness
 *
 */
public final class CoreEngine {
    // Debugging
    private static final String TAG = "HsdCanMonitor";
    private static final boolean D = true;
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
    // Message keys:
    public static final String TOAST = "toast";
    public static final String DEVICE_NAME = "dev_name";
    public static final String RESPONSE = "response";
    public static final String DURATION = "duration";
    // Local Bluetooth adapter
    private BluetoothAdapter _bluetoothAdapter = null;
    // Parent Activity:
    protected Handler _parentHandler;

    // Store references of threads so that we can stop them at will:
    private Thread _interface;
    private Thread _scheduler;
    private Thread _responseHandler;
    
	private CoreEngine() {}
	
	/**
	* Singleton implementation:
	* CoreEngineHolder is loaded on the first execution of CoreEngine.getInstance() 
	* or the first access to CoreEngineHolder.INSTANCE, not before.
	*/
	private static class CoreEngineHolder { 
		public static final CoreEngine INSTANCE = new CoreEngine();
	}
	 
	public static CoreEngine getInstance() {
		return CoreEngineHolder.INSTANCE;
	}
	
	public void startInit() {
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

	public void resumeInit() {
        // Inform the UI that we are not connected yet:
        setState(STATE_NONE);
		// Bluetooth is now up and running:
		scanDevices(); // TODO connect to last known device instead.
		// TODO Should try connecting to the previously connected device right away !
	}
	
	public void scanDevices() {
		Message msg = _parentHandler.obtainMessage(MESSAGE_SCAN_DEVICES);
        _parentHandler.sendMessage(msg);
	}
	
	public void connectToDevice(final String address) {
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

        	}
        }.start();
	}

    /**
     * Set the current state of the bluetooth connection
     * @param state  An integer defining the current connection state
     */
    synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() -> " + state);
        // Give the new state to the Handler so the UI Activity can update
        _parentHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
        switch (state) {
        case STATE_CONNECTED:
        	// We have just connected to the device,
        	// Let's first launch our commands-related threads:
        	startCommandResponseThreads();
        	// Then let's launch our initialization commands:
            if (D) Log.d(TAG, "Running Initialization commands...");
            CommandScheduler.getInstance().startInitCommands();
            break;
        case STATE_CONNECTION_LOST:
        case STATE_CONNECT_FAILED: // or only for LOST?
        	askForToastMessage(R.string.title_connect_failed);
        	// TODO? scanDevices();
            break;
        }
    }
    	
	protected void startCommandResponseThreads() {
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
	
	public void stopAllThreads() {
		// Request graceful stop:
		CanInterface.getInstance().stop();
		CommandScheduler.getInstance().stop();
		ResponseHandler.getInstance().stop();
		// Then interrupt the threads:
		if (_interface != null)
			_interface.stop();
		if (_scheduler != null)
			_scheduler.stop();
		if (_responseHandler != null)
			_responseHandler.stop();
		// Finally remove the references:
		_interface = null;
		_scheduler = null;
		_responseHandler = null;
	}
	
	/**
	 * This method asks the parent UI to display a message.
	 * 
	 * @param resource_id the id of the string in values/strings.xml
	 */
	protected void askForToastMessage(int resource_id) {
    	Message msg = _parentHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putInt(TOAST, resource_id);
        msg.setData(bundle);
    	_parentHandler.sendMessage(msg);
	}

    public void sendManualCommand(final String cmdStr) {
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
							if (resultStr == null) {
						        if (D) Log.d(TAG, "Waiting for response of cmd: " + cmdStr);
								cmd.wait();
							}
							// If we're here, it means we've been woken up by the reception of the response:
							// (or that we had received the reponse before waiting!)
							// Let's notify the UI:
					        if (D) Log.d(TAG, "Response of cmd (" + cmdStr + ") received: " + cmd.getResponseString());
					    	Message msg = _parentHandler.obtainMessage(MESSAGE_COMMAND_RESPONSE);
					        Bundle bundle = new Bundle();
					        bundle.putLong(DURATION, cmd.getDuration());
					        bundle.putString(RESPONSE, cmd.getResponseString());
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

	public void setCurrentHandler(Handler handler) {
		// TODO support multiple handlers?
		_parentHandler = handler;
	}

}
