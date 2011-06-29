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
			// TODO: Treat each byte command separately in sequence, increasing the offset accordingly
			// (instead of having a whole block of commands). This requires remembering which ECU was addressed.
			/*if ("210170718A".equals(cmd._command)) { // Shorter command with only the info displayed on the landscape UI.
				// SOC	%	Tb[Offs + 22]/255
				_formattedBytesResponse.position(24);
				us1 = getNextUnsignedShort();
				resDouble = us1 / 2.55;
				ushort = (int) resDouble;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.STATE_OF_CHARGE,Integer.toString(ushort)));

				// Offs=25 Inverter Temp-(MG1)	°C	=(Tb[Offs + 1] - 40)
				_formattedBytesResponse.position(26);
				ushort = getNextUnsignedShort() - 40;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.INVERTER_TEMP_MG1,Integer.toString(ushort)));

				// Offset=30 Inverter Temp-(MG2)	°C	=(Tb[Offs + 1] - 40) 
				_formattedBytesResponse.position(31);
				ushort = getNextUnsignedShort() - 40;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.INVERTER_TEMP_MG2,Integer.toString(ushort)));

				// Offs=35 Amp	Amp	=(Tb[Offs + 1] * 256 + Tb[Offs + 2] - 128 * 256 ) /100				
				_formattedBytesResponse.position(36);
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				resDouble = Math.round(ushort / 10.0); // Keep only one decimal
				resDouble /= 10; // Put back the one and only decimal.
				if (Math.abs(resDouble) > 3) { // Let's ignore all decimal values:
					res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_AMP,Integer.toString((int)Math.round(resDouble))));
				} else {	// Do not round up for smaller values
					res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_AMP,Double.toString(resDouble)));
				}
			}
			else */ 
			if ("21013C49".equals(cmd._command)) {
				// Initial offset: First byte after command id:
				_formattedBytesResponse.position(3);
				// Offs=2	calc_load	%	=Tb[Offs + 1] / 2,55
				getNextUnsignedShort();
				// ignored:
				getNextUnsignedShort();
				// vehicule_load	%	=Tb[Offs + 3] / 2,55
				resDouble = getNextUnsignedShort() / 2.55;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.VEHICLE_LOAD,Integer.toString((int)Math.round(resDouble))));
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
			else if ("2161626768748A".equals(cmd._command)) {
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

				getNextUnsignedShort(); // Unused...
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
				// DC/DC Cnv Temp (Lower)	°C	Tb[Offs + 2] - 4
				us1 = getNextUnsignedShort() - 40;
				// Send the max temp to the UI:
				res.add(new Pair<Integer, String>(GenericResponseDecoder.DC_CNV_TEMP,Integer.toString(Math.max(us1, ushort))));

				// HVL	Volt	=(Tb[Offs + 6] * 256 + Tb[Offs + 7] ) / 2
				_formattedBytesResponse.position(32);
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				Double battVolt = (us1*256 + getNextUnsignedShort())/2.0;
				//res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_RPM,Integer.toString(ushort)));
				// HVH	Volt	=(Tb[Offs + 8] * 256 + Tb[Offs + 9] ) / 2
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = (us1*256 + getNextUnsignedShort())/2;
				//res.add(new Pair<Integer, String>(GenericResponseDecoder.MG2_RPM,Integer.toString(ushort)));

				_formattedBytesResponse.position(37);
				// Offs=36 Amp	Amp	=(Tb[Offs + 1] * 256 + Tb[Offs + 2] - 128 * 256 ) /100				
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort() - bigShort;
				/*
				resDouble = Math.round(ushort / 10.0); // Keep only one decimal
				resDouble /= 10; // Put back the one and only decimal.
				if (Math.abs(resDouble) > 3) { // Let's ignore all decimal values:
					res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_AMP,Integer.toString((int)Math.round(resDouble))));
				} else {	// Do not round up for smaller values
					res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_AMP,Double.toString(resDouble)));
				}*/
				// Now calculate battery power (P=UI) in kW:
				resDouble = ushort / 100.0; // (in Amps)
				resDouble *= battVolt; // P=UI in W
				// Round up to one decimal while converting to kW:
				resDouble = Math.round(resDouble / 100.0);
				resDouble /= 10;
				if (Math.abs(resDouble) > 5) { // Let's ignore all decimal values:
					res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_KW,Integer.toString((int)Math.round(resDouble))));
				} else {	// Do not round up for smaller values
					res.add(new Pair<Integer, String>(GenericResponseDecoder.BATT_KW,Double.toString(resDouble)));
				}
			}
			else if ("210170718798".equals(cmd._command)) {
				// Initial offset: First byte after command id:
				// Uncomment if Ext_temp: _formattedBytesResponse.position(3);
				// Offs=2	Ext_temp	°C	(Tb[Offs + 4] - 40
				 //TODO if needed...

				// Aux_Batt	volt	(Tb[Offs + 20] * 256 +  Tb[Offs + 21] )/1000
				_formattedBytesResponse.position(22);
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.AUX_BATT,Double.toString(resDouble)));
				// SOC	%	Tb[Offs + 22]/255
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				resDouble = us1 / 2.55;
				ushort = (int) resDouble;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.STATE_OF_CHARGE,Integer.toString(ushort)));

				// Offs=25 Inverter Temp-(MG1)	°C	=(Tb[Offs + 1] - 40)
				_formattedBytesResponse.position(26);
				us1 = getNextUnsignedShort() - 40;
				// Offset=30 Inverter Temp-(MG2)	°C	=(Tb[Offs + 1] - 40) 
				_formattedBytesResponse.position(31);
				ushort = getNextUnsignedShort() - 40;
				// Send only the max value between MG1 and MG2:
				res.add(new Pair<Integer, String>(GenericResponseDecoder.INVERTER_TEMP_MGx,Integer.toString(Math.max(us1, ushort))));
			}
			else if ("2129".equals(cmd._command)) {
				// Offs=1	Fuel_tank	L	=Tb[Offs + 1] / 2
				_formattedBytesResponse.position(2);
				us1 = getNextUnsignedShort();
				resDouble = us1 / 2.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.FUEL_TANK,Double.toString(resDouble)));
			}
			else if ("2181".equals(cmd._command)) { // HV Battery Voltage:
				// Initial offset: First byte after command id:
				_formattedBytesResponse.position(3);
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_01,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_02,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_03,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_04,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_05,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_06,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_07,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_08,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_09,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_10,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_11,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_12,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_13,Double.toString(resDouble)));
				us1 = getNextUnsignedShort(); // first part of the 2-byte short
				ushort = us1*256 + getNextUnsignedShort();
				resDouble = ushort / 1000.0;
				res.add(new Pair<Integer, String>(GenericResponseDecoder.HV_BATT_VOLT_14,Double.toString(resDouble)));
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
