package com.ptc.hsdcanmonitor.commands;

public class BackgroundCommand extends CommandResponseObject {
	public boolean resetOnFailure;
	// If the command does not need to be run at each cycle,
	// increase this (e.g. 5 to run it only once out of 5 cycles
	public int periodicity = 1;
	
	// public String mustContainForSuccess = "OK"; TODO?

	public BackgroundCommand(String command, boolean resetIfFails) {
		super(command);
		resetOnFailure = resetIfFails;
	}

}
