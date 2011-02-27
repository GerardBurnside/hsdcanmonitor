package com.ptc.hsdcanmonitor.commands;

import android.util.Log;

import com.ptc.hsdcanmonitor.commands.GenericResponseDecoder;;

/**
 * This class handles responses from the 2ZR-FXE engines
 * (Toyota: Prius III, Auris HSD, CT 200h)
 * 
 * @authors guinness, priusfan
 *
 */
public class Decoder_2ZR_FXE extends GenericResponseDecoder {
    // Debugging
    private static final String TAG = "HsdCanMonitor";
    private static final boolean D = true;

	@Override
	public void decodeResponse(CommandResponseObject cmd) {
		// TODO Implement me !
		byte[] response = cmd.getResponsePayload(); // Move into decodeResponse?
		if (D) Log.d(TAG, "Payload size = "+response.length);
	}

}
