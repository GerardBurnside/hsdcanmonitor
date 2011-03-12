package com.ptc.android.hsdcanmonitor;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.util.Log;

import com.ptc.android.hsdcanmonitor.commands.LiveMonitoringCommand;
import com.ptc.android.hsdcanmonitor.commands.CommandResponseObject;
import com.ptc.android.hsdcanmonitor.commands.InitCommand;

public class CommandScheduler implements Runnable {
	protected volatile boolean _keepRunning = true;
	// Right now let's not monitor at first, later change it to true:
	protected volatile boolean _runLiveMonitoringCommands = false;
	// Counter used to cycle through background commands:
	private volatile int _counter = 0;
	// Counter of the number of cycles performed:
	private int _cyclesNumber = 1;
	private static final int MAX_CYCLES_NUMBER = 1000; // == higher value of periodicity configured!
	// Object used to wake up when sleeping because nothing to do.
	protected Object _syncObj = new Object();
	// The concurrentLinkedQueue is overkill here, but it shouldn't hurt:
	protected ConcurrentLinkedQueue<CommandResponseObject> manualCommands = new ConcurrentLinkedQueue<CommandResponseObject>();
	protected BlockingQueue<CommandResponseObject> _input;
	// Store the name of the latest parsed commands filename:
	protected String _currentCommandsFilename = null;
	// The current set of live monitoring commands:
	protected ArrayList<LiveMonitoringCommand> _liveMonitoringCommands = new ArrayList<LiveMonitoringCommand>();
    
    private CommandScheduler() {
    	// Private for singleton implem.
		_input = CanInterface.getInstance().getInputQueue();
    }
	/**
	* Singleton implementation:
	* CommandSchedulerHolder is loaded on the first execution of CommandScheduler.getInstance() 
	* or the first access to CommandSchedulerHolder.INSTANCE, not before.
	*/
	private static class CommandSchedulerHolder { 
		public static final CommandScheduler INSTANCE = new CommandScheduler();
	}
	 
	public static CommandScheduler getInstance() {
		return CommandSchedulerHolder.INSTANCE;
	} 

	public void addManualCommands(CommandResponseObject[] cmds) {
		for (CommandResponseObject cmd : cmds)
			manualCommands.add(cmd);
		// Make sure we're ready to handle it:
		resetAndWakeUp();
	}

	public void addManualCommand(CommandResponseObject cmd) {
		manualCommands.add(cmd);
		resetAndWakeUp();
	}

	public void startLiveMonitoringCommands(final boolean doInit) {
		_runLiveMonitoringCommands = true;
		// Run this in a separate Thread because this is called from UI:
        new Thread() {
        	public void run() {
        		if (ResponseHandler.getInstance()._logLiveMonitoringCommands) {
        			ResponseHandler.getInstance().startLoggingCommands();
        		}
        		if (doInit) {
            		sendInitCommands();
        		}
        		resetAndWakeUp();
       	}
        }.start();
	}

	public void stopLiveMonitoringCommands() {
		// We will stop after the current command:
		_runLiveMonitoringCommands = false;
		// By stopping now, we're losing the last command, is this a big deal?
		ResponseHandler.getInstance().endLoggingCommands();
	}

	public boolean isBackgroundMonitoring() {
		return _runLiveMonitoringCommands;
	}

	public void resetAndWakeUp() {
		// wake up the thread in case it was sleeping:
		synchronized (_syncObj) {
			_counter = 0; // Reset background commands cycle.
			_syncObj.notifyAll();
		}
	}
	
	private int incrementCommandCounter() {
		if (_counter >= _liveMonitoringCommands.size()) {
			_counter = 0; // cycle back at the beginning!
			if (++_cyclesNumber > MAX_CYCLES_NUMBER) {
				_cyclesNumber = 1;
			}
		}
		return _counter++; // returned value is not incremented on purpose!
	}

	@Override
	public void run() {
		_keepRunning = true; // Required if it was previously stopped!
		while (_keepRunning) {
			// Treat manual commands in priority (those should be few and time-spaced anyway):
			CommandResponseObject cmd = manualCommands.poll();
			if (cmd == null) {
				// No manual cmd to send? let's check for background ones:
				if (_runLiveMonitoringCommands && _liveMonitoringCommands.size() > 0) {
					LiveMonitoringCommand bkgCmd;
					synchronized (_syncObj) {
						// Get the first command that is supposed to run at that time:
						do {
							bkgCmd = _liveMonitoringCommands.get(incrementCommandCounter());
						}
						while (_cyclesNumber % bkgCmd.periodicity != 0);
						// TODO: we should check that the previous command was "AT SH xxx" if needed!
						// i.e. if we inserted a manual command in between!
						bkgCmd.reset();
					}
					// Let's feed this command to the CanInterface:
					feedInterface(bkgCmd);
				}
				else { // No manual or background Commands, let's go to sleep:
					synchronized (_syncObj) {
						try {
							_syncObj.wait();
						} catch (InterruptedException e) {
							// No big deal, we'll go back to sleep if needed.
						}
						// we've been woken up, let's start over:
						continue;
					}
				}
			}
			else { // Let's feed this command to the CanInterface:
				feedInterface(cmd);
			}
		}
	}

	private void feedInterface(CommandResponseObject cmd) {
		try {
			_input.put(cmd);
		} catch (InterruptedException e) {
			// TODO Try again or abort?
			CoreEngine.setState(CoreEngine.STATE_CONNECTION_LOST); // overkill?
		}
	}

	public void stop() {
		_keepRunning = false;
	}

	protected void sendInitCommands() {
		// TODO: Read once from init_commands.xml instead of hard-coded!
		InitCommand cmd = new InitCommand("ATI");
		feedInterface(cmd);
		cmd = new InitCommand("ATSP6","OK");
		feedInterface(cmd);
		cmd = new InitCommand("ATE0","OK");
		feedInterface(cmd);
		cmd = new InitCommand("ATH1","OK");
		feedInterface(cmd);
		cmd = new InitCommand("ATL0","OK");
		feedInterface(cmd);
		cmd = new InitCommand("ATS0"); // Some dongles donot support this!
		feedInterface(cmd);
		cmd = new InitCommand("ATSH 7E2","OK");
		feedInterface(cmd);
		cmd = new InitCommand("21C3",true); // Special init command for HSD detection!
		cmd.response_decoder_map.put("7EA03","2ZR_FXE.xml");
		cmd.response_decoder_map.put("7EA10","1NZ_FXE.xml");
		feedInterface(cmd);
	}

	public void setMonitoringCommands(String commands_filename) {
		// TODO Parse the actual xml file instead of hardcoding here !!!
		if (commands_filename.equals(_currentCommandsFilename)) {
			return; // Nothing to do.
		}
		_liveMonitoringCommands.clear();
		LiveMonitoringCommand newCmd;
		if ("2ZR_FXE.xml".equals(commands_filename)) {
			// TODO: Load the decoder classname from the xml file!
			LiveMonitoringCommand.loadDecoder("com.ptc.android.hsdcanmonitor.commands.Decoder_2ZR_FXE");
			// Build an Array of LiveMonitoringCommands to cycle through
			// TODO: read them from the xml file instead:
			newCmd = new LiveMonitoringCommand("ATSH 7E0", true);
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("013C3E", false);
			newCmd.periodicity = 100;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("21013C49", false);
			newCmd.periodicity = 1;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("ATSH 7E2", false);
			newCmd.periodicity = 1;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("2161626768748A", false);
			newCmd.periodicity = 1;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("210170718798", false);
			newCmd.periodicity = 10;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("ATSH 7C0", false);
			newCmd.periodicity = 1000;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("2129", false);
			newCmd.periodicity = 1000;
			_liveMonitoringCommands.add(newCmd);
	    	//TODO: Friction Brake Sensor: AT SH 07B0 + 2107
		}
		else if ("1NZ_FXE.xml".equals(commands_filename)) {
			// TODO: Load the decoder classname from the xml file!
			LiveMonitoringCommand.loadDecoder("com.ptc.android.hsdcanmonitor.commands.Decoder_1NZ_FXE");
			// Build an Array of LiveMonitoringCommands to cycle through
			// TODO: read them from the xml file instead:
			
			// TODO: ADAPT FOR PII !!!!!!!!!!!!!!!!!!!!!!!!!
			
			newCmd = new LiveMonitoringCommand("AT SH 7E0", true);
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("013C3E", false);
			newCmd.periodicity = 100;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("21013C49", false);
			newCmd.periodicity = 1;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("AT SH 7E2", false);
			newCmd.periodicity = 1;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("2161626768748A", false);
			newCmd.periodicity = 1;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("210170718798", false);
			newCmd.periodicity = 10;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("AT SH 7C0", false);
			newCmd.periodicity = 1000;
			_liveMonitoringCommands.add(newCmd);
			newCmd = new LiveMonitoringCommand("2129", false);
			newCmd.periodicity = 1000;
			_liveMonitoringCommands.add(newCmd);
		}
		else if ("hv_batt_PIII.xml".equals(commands_filename)) {
			// TODO: Load the decoder classname from the xml file!
			LiveMonitoringCommand.loadDecoder("com.ptc.android.hsdcanmonitor.commands.Decoder_HvBatt_2ZR_FXE");			
			// Build an Array of LiveMonitoringCommands to cycle through
			// TODO: read them from the xml file instead:
			manualCommands.add(new LiveMonitoringCommand("AT SH 7E2", true));
			// Cycle on a single command:
			newCmd = new LiveMonitoringCommand("2181", false);
			_liveMonitoringCommands.add(newCmd);
		}
		else if (CoreEngine.D) Log.e(CoreEngine.TAG, "Unable to parse file: " + commands_filename);
		// Cache the latest xml file read:
		_currentCommandsFilename = commands_filename;
		resetAndWakeUp();
	}	
}
