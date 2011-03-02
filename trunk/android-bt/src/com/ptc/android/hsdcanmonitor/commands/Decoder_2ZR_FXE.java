package com.ptc.android.hsdcanmonitor.commands;

import java.util.ArrayList;

import android.util.Log;
import android.util.Pair;

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
	public ArrayList<Pair<Integer, String>> decodeResponse(CommandResponseObject cmd, String ecu) {
		ArrayList<Pair<Integer, String>> res = null;
		// In order to avoid Garbage collection, let's re-use
		// the same buffer for all commands; the returned len
		// tells us when to stop reading the buffer.
		int len = cmd.getResponsePayload(_formattedBytesResponse);
		if (D) Log.d(TAG, "Payload size = "+len);
		if (len > 0) {
			res = new ArrayList<Pair<Integer, String>>();
			// TODO Implement me !
			// TODO, use command names for better readability?
			if ("2161626768748A".equals(cmd._command)) {
				final int bigShort = 0x8000;
				// Test: retrieve the Amps drawn from the HV BATT:
				// TODO: parse the buffer until this point:
				_formattedBytesResponse.position(37); // Offset+1 from andrius.xls
				int us1 = getNextUnsignedShort(); // first part of the 2-byte short
				int ushort = us1*256 + getNextUnsignedShort(); // add to 2nd part.
				int reste = ushort - bigShort;
				double amps = reste / 100.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_AMP,Double.toString(amps)));
			}
		}
		// Might be null:
		return res;
	}
	
	private short getNextUnsignedShort() {
		return (short)(_formattedBytesResponse.get() & 0xff);
	}

}
