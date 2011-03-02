package com.ptc.android.hsdcanmonitor.commands;

import android.util.Log;

import com.ptc.android.hsdcanmonitor.commands.GenericResponseDecoder;

/**
 * This class handles responses from the 2ZR-FXE engines
 * (Toyota: Prius III, Auris HSD, CT 200h)
 * 
 * @authors guinness, priusfan
 *
 */
public class Decoder_2ZR_FXE extends GenericResponseDecoder {

	@Override
	public void decodeResponse(CommandResponseObject cmd, String ecu) {
		// TODO Implement me !
		// In order to avoid Garbage collection, let's re-use
		// the same buffer for all commands; the returned len
		// tells us when to stop reading the buffer.
		int len = cmd.getResponsePayload(_formattedBytesResponse);
		if (D) Log.d(TAG, "Payload size = "+len);
	}

}
