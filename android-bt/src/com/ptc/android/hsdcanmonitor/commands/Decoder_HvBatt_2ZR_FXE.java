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
public class Decoder_HvBatt_2ZR_FXE extends GenericResponseDecoder {

	@Override
	public ArrayList<Pair<Integer, String>> decodeResponse(CommandResponseObject cmd) {
		ArrayList<Pair<Integer, String>> res = null;
		// In order to avoid Garbage collection, let's re-use
		// the same buffer for all commands; the returned len
		// tells us when to stop reading the buffer.
		int len = cmd.getResponsePayload(_formattedBytesResponse);
		if (D) Log.d(TAG, "Payload size = "+len);
		if (len > 0) {
			int us1;
			int ushort;
			double resDouble;
			res = new ArrayList<Pair<Integer, String>>();
			// Initial offset: First byte after command id:
			_formattedBytesResponse.position(3);
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_01,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_02,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_03,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_04,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_05,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_06,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_07,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_08,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_09,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_10,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_11,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_12,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_13,Double.toString(resDouble)));
			us1 = getNextUnsignedShort(); // first part of the 2-byte short
			ushort = us1*256 + getNextUnsignedShort();
			resDouble = ushort / 1000.0;
			res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_VOLT_14,Double.toString(resDouble)));
		}
		// Might be null:
		return res;
	}
	
	/**
	 * This method is final so that compiler makes it inline.
	 * @return
	 */
	final private short getNextUnsignedShort() {
		return (short)(_formattedBytesResponse.get() & 0xff);
	}

}
