package com.ptc.hsdcanmonitor;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.BlockingQueue;

import com.ptc.android.hsdcanmonitor.R;
import com.ptc.hsdcanmonitor.commands.BackgroundCommand;
import com.ptc.hsdcanmonitor.commands.CommandResponseObject;
import com.ptc.hsdcanmonitor.commands.Decoder_2ZR_FXE;
import com.ptc.hsdcanmonitor.commands.GenericResponseDecoder;
import com.ptc.hsdcanmonitor.commands.InitCommand;

import android.os.Environment;
import android.util.Log;

public class ResponseHandler implements Runnable {
    // Debugging
    private static final String TAG = "HsdCanMonitor";
    private static final boolean D = true;
    // For gentle thread stopping:
	protected volatile boolean _keepRunning = true;
    // Shall we log commands/responses or not:
	protected volatile boolean _logBackgroundCommands = true;
	// Reference to the handler that will actually interpret the response:
	protected GenericResponseDecoder _decoder;
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
		while (_keepRunning) {
			try {
				handleCommand(responses.take());
			} catch (InterruptedException ex) {
				// TODO?
			}
		}
	}

	private void handleCommand(CommandResponseObject request) {
		// If manual command, notify the sender with the raw response:
		request.notifySender();
		if (request instanceof BackgroundCommand) { // Not O-O but I don't care :-)

			// TODO Pre-format the responses, keeping only the useful bytes:
			decodeResponses((BackgroundCommand)request);
			
			// For now (until UI is available), just log the raw data:
			if (_logBackgroundCommands) {  // TODO: should be configurable!
				logCommand(request);
			}
		}
		else if (request instanceof InitCommand && request.hasTimedOut()) {
			// Notify of init pb:
			CoreEngine.getInstance().askForToastMessage(R.string.msg_init_failure);
		}
		// else: the sender has probably already been notified
	}
    
	private void decodeResponses(BackgroundCommand request) {
    	if (request.hasTimedOut()) {
	        if (D) Log.d(TAG, "Response of cmd (" + request.getCommand() + ") timed out!");
    		if (request.resetOnFailure) {
		        if (D) Log.d(TAG, "Restarting the cycle from the beginning because of this error.");
    			CommandScheduler.getInstance().resetAndWakeUp();
    		}
    	}
    	else {
    		if (request.getCommand().startsWith("AT"))
    			return; // Not much to interpret there...
    		// TODO: I might decide to integrate the "AT" command and the following ones
    		// within a single class so they become an atomic command:
    		// it will avoid having to deal with interruption from manual commands
    		// and most importantly it shall make decoding easier !!!
    		
    		// TODO Interpret the responses and store values in a ConcurrentHashMap for the UI to retrieve.
    		
    		if (_decoder == null) // return;
    		{
    			// TODO: this class should be loaded after the init phase,
    			// once we have determined which HSD we're dealing with...
    			// Until then, only 2010 HSD is supported:
    			/* Hum !? the following throws a ClassNotFoundException:
    			String decoderClassName = "com.ptc.hsdcanmonitor.commands.Decoder_2ZR_FXE";
    			try {
					Class decoder = ClassLoader.getSystemClassLoader().loadClass(decoderClassName);
					_decoder = (GenericResponseDecoder) decoder.newInstance();
				} catch (Throwable e) {
					if (D) Log.e(TAG, "unable to load class: "+decoderClassName, e);
					return;
				}*/
    			_decoder = new Decoder_2ZR_FXE();
    		}
    		// else:
    		_decoder.decodeResponse(request);
    	}
	}

	private void logCommand(CommandResponseObject request) {
    	if (_currentLogFile != null) {
        	// TODO: Write in a way easily exported to spreadsheet tools:
    		LogData("Sent: " + request.getCommand() + "\n");
    		LogData("Received ("+request.getDuration()+"ms): " + request.getResponseString()+"\n");
    	}
    	// else, ignore this output, probably an init or manual command..
    }
    
    public void startLoggingCommands() {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
        	CoreEngine.getInstance().askForToastMessage(R.string.sd_card_not_mounted);
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
    }

}
