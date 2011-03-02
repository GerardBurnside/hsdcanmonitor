package com.ptc.android.hsdcanmonitor.commands;

public class InitCommand extends CommandResponseObject {

	// Nothing special for the moment, but maybe we should enforce success
	// and at least notify failure !!!
	
	public InitCommand(String command) {
		super(command);
	}

}
