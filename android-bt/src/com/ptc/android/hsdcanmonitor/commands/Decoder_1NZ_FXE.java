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
public class Decoder_1NZ_FXE extends GenericResponseDecoder {

	// TODO This is currently PIII stuff !!!
	
	@Override
	public ArrayList<Pair<Integer, String>> decodeResponse(CommandResponseObject cmd) {
		ArrayList<Pair<Integer, String>> res = null;
		// In order to avoid Garbage collection, let's re-use
		// the same buffer for all commands; the returned len
		// tells us when to stop reading the buffer.
		int len = cmd.getResponsePayload(_formattedBytesResponse);
		if (D) Log.d(TAG, "Payload size = "+len);
		if (len > 0) {
			final int bigShort = 0x8000;
			int us1;
			int ushort;
			double resDouble;
			res = new ArrayList<Pair<Integer, String>>();
			// TODO, use command names for better readability?
			if ("2161626768748A".equals(cmd._command)) {
				// Initial offset: First byte after command id, then 2 ignored bytes:
				_formattedBytesResponse.position(5);
				// Offs=2	MG1_Temp	°C	=Tb[Offs + 3] - 40
				// MG1_Temp	°C	=Tb[Offs + 3] - 40:
				ushort = getNextUnsignedShort() - 40;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.MG1_TEMP,Integer.toString(ushort)));
				// MG1_RPM		=Tb[Offs + 4] * 256 + Tb[Offs + 5] - 128 * 256
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.MG1_RPM,Integer.toString(ushort)));
				
				_formattedBytesResponse.position(11);
				// Offs=8	MG2_Temp	°C	=Tb[Offs + 3] - 40						
				ushort = getNextUnsignedShort() - 40;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_TEMP,Integer.toString(ushort)));
				// MG2_RPM		=Tb[Offs + 4] * 256 + Tb[Offs + 5] - 128 * 256
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_RPM,Integer.toString(ushort)));
				// Offs=14	MG1_Torque	NM	=(Tb[Offs + 1] * 256 + Tb[Offs + 2] -128*256) / 8
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				resDouble = ushort / 8.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.MG1_TORQUE,Integer.toString(ushort)));

				_formattedBytesResponse.position(21);
				// Offs=20	MG2_Torque	NM	=(Tb[Offs + 1] * 256 + Tb[Offs + 2] -128*256) / 8
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				resDouble = ushort / 8.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_TORQUE,Integer.toString(ushort)));

				_formattedBytesResponse.position(27);
				// Offs=26	DC/DC Cnv Temp (Upper)	°C	Tb[Offs + 1] - 40
				ushort = getNextUnsignedShort() - 40;
				//res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_TEMP,Integer.toString(ushort)));
				// DC/DC Cnv Temp (Lower)	°C	Tb[Offs + 2] - 4
				ushort = getNextUnsignedShort() - 40;
				//res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_TEMP,Integer.toString(ushort)));
				// HVL	Volt	=(Tb[Offs + 6] * 256 + Tb[Offs + 7] ) / 2
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				//res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_RPM,Integer.toString(ushort)));
				// HVH	Volt	=(Tb[Offs + 8] * 256 + Tb[Offs + 9] ) / 2
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				//res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_RPM,Integer.toString(ushort)));

				_formattedBytesResponse.position(37);
				// Offs=36 Amp	Amp	=(Tb[Offs + 1] * 256 + Tb[Offs + 2] - 128 * 256 ) /100				
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				resDouble = ushort / 100.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_AMP,Double.toString(resDouble)));
			}
			else if ("21013C49".equals(cmd._command)) {
				// Initial offset: First byte after command id:
				_formattedBytesResponse.position(3);
				// Offs=2	calc_load	%	=Tb[Offs + 1] / 2,55
				getNextUnsignedShort();
				// ignored:
				getNextUnsignedShort();
				// vehicule_load	%	=Tb[Offs + 3] / 2,55
				getNextUnsignedShort();
				// MAF	G/S	=(Tb[Offs + 4]*256 + Tb[Offs + 5]) / 100
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				resDouble = ushort / 100.0;
				// MAP	kPa	=Tb[Offs + 6]
				getNextUnsignedShort();
				// Air_Intake_Temp	°C	=Tb[Offs + 7] - 40
				getNextUnsignedShort();
				// Atm_Press	kPa	=Tb[Offs + 8]
				getNextUnsignedShort();
				// ICE_Temp	°C	=Tb[Offs + 9] - 40
				ushort = getNextUnsignedShort() - 40;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.ICE_TEMP,Integer.toString(ushort)));
				// ICE_RPM		=(Tb[Offs + 10] * 256 + Tb[Offs + 11] ) / 4
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = (us1*256 + getNextUnsignedShort())/4;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.ICE_RPM,Integer.toString(ushort)));
				// Speed 	kM/H	=Tb[Offs + 12]
				getNextUnsignedShort();

				_formattedBytesResponse.position(29);
				// Offs=28	Inj_µL	µL	=Tb[Offs + 1] * 256 + Tb[Offs + 2]
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = (us1*256 + getNextUnsignedShort());
				// inj_µS	µS	=Tb[Offs + 3] * 256 + Tb[Offs + 4]	
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = (us1*256 + getNextUnsignedShort());

				_formattedBytesResponse.position(38);
				// Offs=34	ICE_Torque	NM	=(Tb[Offs + 4]*256 + Tb[Offs + 5]) - 128*256
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = (us1*256 + getNextUnsignedShort()) - bigShort;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.ICE_TORQUE,Integer.toString(ushort)));
			}
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
