package com.ptc.android.hsdcanmonitor.commands;

import java.util.HashMap;

import com.ptc.android.hsdcanmonitor.CommandScheduler;
import com.ptc.android.hsdcanmonitor.CoreEngine;
import com.ptc.android.hsdcanmonitor.R;

import android.util.Log;

public class InitCommand extends CommandResponseObject {

	protected String _failIfResponseDoesNotContain = null;
	// Special command for HSD detection (coud have done a separate class but this one is so small anyway):
	protected boolean _isHsdDetectionCmd = false;
	public HashMap<String, String> response_decoder_map; // key=response, value=xml_filename>.
	// End special command for HSD detection.
	
	public InitCommand(String command) {
		super(command);
	}
	public InitCommand(String command, String failIfResponseDoesNotContain) {
		super(command);
		_failIfResponseDoesNotContain = failIfResponseDoesNotContain;
	}
	
	// Special constructor for HSD detection command:
	public InitCommand(String command, boolean isHsdDetectionCmd) {
		super(command);
		_isHsdDetectionCmd = isHsdDetectionCmd;
		response_decoder_map = new HashMap<String, String>();
	}
	// End special methods for HSD detection command.
	
	/**
	 * This method analyzes the response from the init command.
	 * @return true if everything is fine, false if the command failed.
	 */
	public void analyzeResponse() {
		if (hasTimedOut()) {
			initProblem();
			return;
		}
		if (_isHsdDetectionCmd) {
			// Check which HSD version we're dealing with and load the appropriate resources:
			detectHSD();
			return;
		}
		final String respStr = _rawStringResponse.toString().replaceAll(" ", "");
		if (_failIfResponseDoesNotContain != null
			&& !respStr.contains(_failIfResponseDoesNotContain)) {
			// We are not happy with the response!
			if (D) Log.e(TAG, "INIT cmd did not contain expected value: " + _failIfResponseDoesNotContain);
			initProblem();
		}
	}
	
	private void initProblem() {
		CoreEngine.askForToastMessage(R.string.msg_init_failure);
	}
	
	private void detectHSD() {
		String respStr = _rawStringResponse.toString();
		// Check if ATS0 was a success or not...
		if (_rawStringResponse.indexOf(" ") < 0) {
			ats0_supported = true;
		}
		else {
			ats0_supported = false;
			respStr = respStr.replaceAll(" ", "");
		}

		for (String respStartsWith : response_decoder_map.keySet()) {
			if (respStr.startsWith(respStartsWith)) {
				// Bingo, let's load the correct set of commands for the CommandScheduler
				// (which will in turn load the corresponding decoder class):
				CommandScheduler.getInstance().setMonitoringCommands(response_decoder_map.get(respStartsWith));
				return;
			}
		}
		// TODO: Load a non HSD decoder class (RPM, temp, etc.) by default?
		if (D) Log.e(TAG, "HSD Detection cmd did not contain any expected value!");
		initProblem();
	}
}
