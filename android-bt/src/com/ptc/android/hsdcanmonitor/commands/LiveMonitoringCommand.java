package com.ptc.android.hsdcanmonitor.commands;

import java.util.ArrayList;

import android.util.Log;
import android.util.Pair;

import com.ptc.android.hsdcanmonitor.CommandScheduler;
import com.ptc.android.hsdcanmonitor.CoreEngine;
import com.ptc.android.hsdcanmonitor.R;
import com.ptc.android.hsdcanmonitor.ResponseHandler;

public class LiveMonitoringCommand extends CommandResponseObject {

	// Reference to the handler that will actually interpret the response:
	protected static GenericResponseDecoder _decoder;

	
	public boolean resetOnFailure;
	// If the command does not need to be run at each cycle,
	// increase this (e.g. 5 to run it only once out of 5 cycles
	public int periodicity = 1;
	
	public LiveMonitoringCommand(String command, boolean resetIfFails) {
		super(command);
		resetOnFailure = resetIfFails;
	}
	
	public static void loadDecoder(String decoderClassName) {
	    try {
			Class<?> myClass = Class.forName(decoderClassName, true, GenericResponseDecoder.class.getClassLoader());//myClassLoader);
			Object decoder = myClass.newInstance();
			if (decoder instanceof GenericResponseDecoder) {
				_decoder = (GenericResponseDecoder)decoder;
				// Let the user know:
				String[] pkgs = decoderClassName.split("\\."); // Retrieving only the classname:
				CoreEngine.askForToastMessageWithParam(R.string.msg_decoder_loaded, pkgs[pkgs.length-1]);				
			}
			else // WTF?
				if (D) Log.e(TAG, "Class: "+decoderClassName + " must extend GenericResponseDecoder!");
		} catch (Throwable e) {
			if (D) Log.e(TAG, "unable to load class: "+decoderClassName, e);
		}
	}

	
	public void analyzeResponse() {
		
		ResponseHandler.getInstance().logCommand(this);
		
    	if (hasTimedOut()) {
	        if (D) Log.d(TAG, "Response of cmd (" + _command + ") timed out!");
    		if (resetOnFailure) {
		        if (D) Log.d(TAG, "Restarting the cycle from the beginning because of this error.");
    			CommandScheduler.getInstance().resetAndWakeUp();
    		}
    	}
    	else {
    		if (_command.startsWith("AT SH")) {
    			return; // Not much to interpret there...
    		}
    		if (_decoder != null) {
    			ArrayList<Pair<Integer, String>> res = _decoder.decodeResponse(this);
    			if (res != null)
    				CoreEngine.notifyUI(res);
    		}
			// else?
    	}
	}


}
