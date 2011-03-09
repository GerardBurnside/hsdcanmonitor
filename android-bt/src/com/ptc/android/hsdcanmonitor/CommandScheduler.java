package com.ptc.android.hsdcanmonitor;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.ptc.android.hsdcanmonitor.commands.BackgroundCommand;
import com.ptc.android.hsdcanmonitor.commands.CommandResponseObject;
import com.ptc.android.hsdcanmonitor.commands.InitCommand;

public class CommandScheduler implements Runnable {
	protected volatile boolean _keepRunning = true;
	// Right now let's not monitor at first, later change it to true:
	protected volatile boolean _runBackgroundCommands = false;
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
	protected ArrayList<BackgroundCommand> _backgroundCommands = new ArrayList<BackgroundCommand>();
    // List of requests: // TODO read them from a file instead !!!
    private final String[] mInitCommandList = {
    	"ATI", "ATSP6", "ATE0", "ATH1", "ATL0", "ATS0"
    };
    private final String[] mBackgroundCommandsList = {
    	"AT SH 7E0",
    		"013C3E", // periodicity 100
    		"21013C49",
    	"AT SH 7E2", // periodicity 1
    		"2161626768748A", // periodicity 1
    		"210170718798", // periodicity 10 => HAS TO BE MULTIPLE OF "AT SH"
    	"AT SH 7C0", // periodicity 1000
    		"2129" // periodicity 1000
    };
    
    private CommandScheduler() {
    	// Private for singleton implem.
		_input = CanInterface.getInstance().getInputQueue();
		// Build an Array of BackgroundCommands to cycle through
		// TODO: read them from an xml file instead:
		for (String cmdStr : mBackgroundCommandsList) {
			// Force to start over if an "AT" command fails:
			boolean resetIfFails = cmdStr.startsWith("AT SH");
			BackgroundCommand newCmd = new BackgroundCommand(cmdStr, resetIfFails);
			// ******************* Hardcoded tests for peridocity:
			if ("013C3E".equals(cmdStr))
				newCmd.periodicity = 100;
			else if ("AT SH 7E2".equals(cmdStr))
				newCmd.periodicity = 1;
			else if ("2161626768748A".equals(cmdStr))
				newCmd.periodicity = 1;
			else if ("210170718798".equals(cmdStr))
				newCmd.periodicity = 10;
			else if ("AT SH 7C0".equals(cmdStr))
				newCmd.periodicity = 1000;
			else if ("2129".equals(cmdStr))
				newCmd.periodicity = 1000;
			// ******************** End hardcoded tests.
			_backgroundCommands.add(newCmd);
		}
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

	public void startBackgroundCommands() {
		_runBackgroundCommands = true;
		// Run this in a separate Thread because this is called from UI:
        new Thread() {
        	public void run() {
        		if (ResponseHandler.getInstance()._logBackgroundCommands) {
        			ResponseHandler.getInstance().startLoggingCommands();
        		}
        		sendInitCommands();
        		resetAndWakeUp();
       	}
        }.start();
	}

	public void stopBackgroundCommands() {
		// We will stop after the current command:
		_runBackgroundCommands = false;
		// By stopping now, we're losing the last command, is this a big deal?
		ResponseHandler.getInstance().endLoggingCommands();
	}

	public boolean isBackgroundMonitoring() {
		return _runBackgroundCommands;
	}

	public void resetAndWakeUp() {
		// wake up the thread in case it was sleeping:
		synchronized (_syncObj) {
			_counter = 0; // Reset background commands cycle.
			_syncObj.notifyAll();
		}
	}
	
	private int incrementCommandCounter() {
		if (_counter >= _backgroundCommands.size()) {
			_counter = 0; // cycle back at the beginning!
			if (++_cyclesNumber > MAX_CYCLES_NUMBER) {
				_cyclesNumber = 1;
			}
			// Test DEBUG: Only one cycle: stopBackgroundCommands();
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
				if (_runBackgroundCommands) {
					BackgroundCommand bkgCmd;
					synchronized (_syncObj) {
						// Get the first command that is supposed to run at that time:
						do {
							bkgCmd = _backgroundCommands.get(incrementCommandCounter());
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
							// TODO ?
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
		// TODO Auto-generated method stub
		for (int k=0; k<mInitCommandList.length; k++) {
			CommandResponseObject cmd = new InitCommand(mInitCommandList[k]);
			feedInterface(cmd);
		}
		// TODO: We should actually enforce successful responses to init commands:
		CoreEngine.askForToastMessage(R.string.msg_init_done);
	}

}
