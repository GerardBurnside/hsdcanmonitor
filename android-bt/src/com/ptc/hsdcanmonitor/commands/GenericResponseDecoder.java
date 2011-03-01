package com.ptc.hsdcanmonitor.commands;

import java.nio.ByteBuffer;

import com.ptc.hsdcanmonitor.CoreEngine;

//import java.util.concurrent.ConcurrentHashMap;

public abstract class GenericResponseDecoder {
    // Debugging
    protected static final String TAG = "HsdCanMonitor";
    protected static final boolean D = true;

	public static final String STATE_OF_CHARGE = "STATE_OF_CHARGE"; // unit is %
	public static final String HV_BATT_VOLT = "HV_BATT_VOLT";
	public static final String BATT_AMP = "BATT_AMP";
	public static final String BATT_KW = "BATT_KW";
	public static final String ICE_KW = "ICE_KW";
	public static final String ICE_TEMP = "ICE_TEMP";
	public static final String ICE_TORQUE = "ICE_TORQUE";
	public static final String ICE_RPM = "ICE_RPM";
	public static final String MG1_KW = "MG1_KW";
	public static final String MG1_TEMP = "MG1_TEMP";
	public static final String MG1_TORQUE = "MG1_TORQUE";
	public static final String MG1_RPM = "MG1_RPM";
	public static final String MG2_KW = "MG2_KW";
	public static final String MG2_TEMP = "MG2_TEMP";
	public static final String MG2_TORQUE = "MG2_TORQUE";
	public static final String MG2_RPM = "MG2_RPM";
	public static final String COOLANT_TEMP = "COOLANT_TEMP";
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



	public abstract void decodeResponse(CommandResponseObject cmd);
	
	/**
	 * Encapsulated in case we decide to send the information differently:
	 */
	protected void sendResultToUI(/*TODO*/) {
		CoreEngine.getInstance().notifyUI();
	}
}
