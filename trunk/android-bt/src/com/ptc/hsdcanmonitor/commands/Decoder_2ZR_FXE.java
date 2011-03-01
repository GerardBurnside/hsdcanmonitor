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

	@Override
	public void decodeResponse(CommandResponseObject cmd) {
		// TODO Implement me !
		// In order to avoid Garbage collection, let's re-user
		// the same buffer for all commands; the returned len
		// tells us when to stop reading the buffer.
		int len = cmd.getResponsePayload(_formattedBytesResponse);
		if (D) Log.d(TAG, "Payload size = "+len);
	}

}
