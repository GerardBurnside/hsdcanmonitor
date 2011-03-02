package com.ptc.android.hsdcanmonitor.commands;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import android.util.Pair;

//import java.util.concurrent.ConcurrentHashMap;

public abstract class GenericResponseDecoder {
    // Debugging
    protected static final String TAG = "HsdCanMonitor";
    protected static final boolean D = true;

	public static final int STATE_OF_CHARGE = 0; // unit is %
	public static final int HV_BATT_VOLT = 1;
	public static final int BATT_AMP = 2;
	public static final int BATT_KW = 3;
	public static final int ICE_KW = 4;
	public static final int ICE_TEMP = 5;
	public static final int ICE_TORQUE = 6;
	public static final int ICE_RPM = 7;
	public static final int MG1_KW = 8;
	public static final int MG1_TEMP = 9;
	public static final int MG1_TORQUE = 10;
	public static final int MG1_RPM = 11;
	public static final int MG2_KW = 12;
	public static final int MG2_TEMP = 13;
	public static final int MG2_TORQUE = 14;
	public static final int MG2_RPM = 15;
	public static final int COOLANT_TEMP = 16;
    /* TODO: Declare Static finals for these:
	Dis_Max	Discharge Max (kW)	 
	Cha_Max	Charge Max (kW)	 
	Batt_ref_tmp	Batt resfresh Temp (°C)	 
	batt_TB1	Batt Tmp1	 
	batt_TB2	Batt Tmp2	 
	batt_TB3	Batt Tmp3	 
	HVH	HighVoltage	 
	Cnv_Temp	°C	 
	Cool_Temp	°C	 
	Invt_Temp_MG2	°C	 
	Invt_Temp_MG1	°C	 
	Inj_muL	 	 
	FF_mLS	FuelFlow mL/S	 
	FF_LH	FuelFlow (calc) L/H	 
	Cons_kM_L	 	 
	Cons_L_100	 	 
	CalcLoad	%	 
	VehLoad	%	 
	MAF	G/S	 
	MAP	kPa	 
	Air_Intake_Temp	°C	 
	Acc_Pedal	%	 
	Atm_Press	kPa	 
	Ext_Temp	°C	 
	Aux_Batt	V	 
	Speed	kM/H	 
	Distance	kM
	 */
	// Variable used to store the command responses (GC optimization):
	protected ByteBuffer _formattedBytesResponse = ByteBuffer.allocate(1024);


	/**
	 * Actual implementation of this method is deferred to specialized class...
	 * @param cmd The command object to decode
	 * @param ecu The ECU to which the command was sent
	 * @return A list of Pair<id,value> for the decoded values
	 */
	public abstract ArrayList<Pair<Integer, String>> decodeResponse(CommandResponseObject cmd, String ecu);
	
}
