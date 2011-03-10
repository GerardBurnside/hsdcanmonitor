package com.ptc.android.hsdcanmonitor;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.BlockingQueue;

import com.ptc.android.hsdcanmonitor.R;
import com.ptc.android.hsdcanmonitor.commands.CommandResponseObject;

import android.os.Environment;
import android.util.Log;

public class ResponseHandler implements Runnable {
    // Debugging
    private static final String TAG = "HsdCanMonitor";
    private static final boolean D = CoreEngine.D;
    // For gentle thread stopping:
	protected volatile boolean _keepRunning = true;
    // Shall we log commands/responses or not:
	protected volatile boolean _logLiveMonitoringCommands = false;
    // Base filename for logs to the SD card:
    private final File mLogFileDir = Environment.getExternalStoragePublicDirectory("PriusLog");
    private OutputStream _currentLogFile = null;
    private static final String DATE_FORMAT_SECONDS = "yyyy-MM-dd_HHmmss";
    private Calendar mCalendar = Calendar.getInstance();
    private SimpleDateFormat mLogDateFormat = new SimpleDateFormat(DATE_FORMAT_SECONDS);
    private static final String DATE_FORMAT_MILLIS = "HH:mm:ss:SSS";
    private SimpleDateFormat mDateFormatMillis = new SimpleDateFormat(DATE_FORMAT_MILLIS);    

    private ResponseHandler() {
    	// Private for singleton implem.
    }
	/**
	* Singleton implementation:
	* ResponseHandlerHolder is loaded on the first execution of ResponseHandler.getInstance() 
	* or the first access to ResponseHandlerHolder.INSTANCE, not before.
	*/
	private static class ResponseHandlerHolder { 
		public static final ResponseHandler INSTANCE = new ResponseHandler();
	}
	 
	public static ResponseHandler getInstance() {
		return ResponseHandlerHolder.INSTANCE;
	} 

	public void stop() {
		_keepRunning = false;
		endLoggingCommands();
	}

	public void run() {
		BlockingQueue<CommandResponseObject> responses = CanInterface.getInstance().getOutputQueue();
		_keepRunning = true; // Required if it was previously stopped!
		while (_keepRunning) {
			try {
				responses.take().analyzeResponse();
			} catch (InterruptedException ex) {
				// TODO?
			}
		}
	}
    
	public boolean isLoggingEnabled() {
		return _logLiveMonitoringCommands;
	}

	public void logToFileEnabled(boolean b) {
		// TODO this should be stored in a preference settings..
		if (b != _logLiveMonitoringCommands) {
			_logLiveMonitoringCommands = b;
			if (b) {
	        	if (CommandScheduler.getInstance()._runLiveMonitoringCommands) {
					startLoggingCommands();
	        	}
	        	else {
	        		// The user wants to log, log only
	        		// the manual commands or start monitoring?
	        	}
			}
			else { // Close the file properly:
				endLoggingCommands();
			}
		}
		// else already done..
	}

	public void logCommand(CommandResponseObject request) {
    	if (_logLiveMonitoringCommands && _currentLogFile != null) {
        	// TODO: Write in a way easily exported to spreadsheet tools:
    		LogData("Sent: " + request.getCommand() + "\tReceived ("+request.getDuration()+"ms): " + request.getResponseString()+"\n");
    	}
    	// else, ignore this output, probably an init or manual command..
    }
    
    public void startLoggingCommands() {
    	if (!_logLiveMonitoringCommands)
    		return;
    	// Else:
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
        	CoreEngine.askForToastMessage(R.string.sd_card_not_mounted);
        	return;
        }
    	// Create the log file with a name containing the date & time.
    	mLogFileDir.mkdirs(); // Create directories if needed.
    	if (_currentLogFile != null) {
	        if(D) Log.e(TAG, "Trying to open the already opened SD card file !!!!");
        	return;
    	}
        File newlogFile = new File(mLogFileDir,mLogDateFormat.format(mCalendar.getTime())+".txt");
        try {
            _currentLogFile = new BufferedOutputStream(new FileOutputStream(newlogFile));
		} catch (IOException e) {
	        if(D) Log.e(TAG, "Failed to create file for logging!", e);
		}
		CoreEngine.askForToastMessage(R.string.msg_logging_to_file);
    }
    
    private void LogData(String msg) {
    	String logMessage = mDateFormatMillis.format(System.currentTimeMillis())+" "+msg;
    	try {
			_currentLogFile.write(logMessage.getBytes());
		} catch (Throwable e) {
	        if(D) Log.e(TAG, "Failed to write into log file!", e);
		}
    }
    
    public void endLoggingCommands() {
    	if (_currentLogFile == null)
    		return; // Nothing to do.
    	// else:
    	// Close the log file:
    	try {
    		_currentLogFile.flush();
			_currentLogFile.close();
		} catch (IOException e) {
	        if(D) Log.e(TAG, "Failed to close log file!", e);
		}
    	_currentLogFile = null;
		CoreEngine.askForToastMessage(R.string.msg_stop_logging_to_file);
    }

}
