package com.ptc.android.hsdcanmonitor.commands;

import java.nio.ByteBuffer;

import android.util.Log;

import com.ptc.android.hsdcanmonitor.CanInterface;
import com.ptc.android.hsdcanmonitor.CoreEngine;

/**
 * 
 * This class is used to pass a command to the CanInterface
 * and store the result of this command.
 * 
 * @author guinness, priusfan
 *
 */
public class CommandResponseObject {
    // Debugging
    protected static final String TAG = "HsdCanMonitor";
    protected static final boolean D = CoreEngine.D;

    protected static final String UNLUCKY = "Timed Out!";

	// Global flag to know whether the device supports the ATS0 command (i.e. can it remove spaces or not):
	public static boolean ats0_supported = true; // Assume true by default.

    // Possible specific timeout for a command that might take longer than usual:
    public long specific_timeout_value = CanInterface.DEFAULT_RESPONSE_TIME_OUT;
    // Optional for prettier code in the decoder:
    public String cmd_name = "undefined";

	protected String _command; // The actual command to send.
	// Buffer for the raw response:
	protected StringBuilder _rawStringResponse = new StringBuilder();
	protected long _duration;
	protected boolean _timedOut = false; // let's not be pessimistic ;-)
	// Optional, to notify the sender that the response is available (for Manual Commands)
	protected Object _notifyMe;
	// In order to reduce garbage collection (lots of cycles!),
	// let's create most of our objects only once.

	public CommandResponseObject(String command) {
		_command = command;
	}

	public String getCommand() {
        return _command;
	}
	public byte[] getCommandToSend() {
		// Add \n\r to all commands before sending:
        return (_command + "\n\r").getBytes();
	}

	public void setRawResponse(char[] resp, int len) {
		_rawStringResponse.append(resp,0,len);
	}

	public void setSender(Object obj) {
		_notifyMe = obj;
	}
	/**
	 * Returns true if a sender was notified, false if nobody registered.
	 */
	public boolean notifySender() {
		if (_notifyMe != null) {
			synchronized (_notifyMe) {
				_notifyMe.notifyAll();
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Should only be called for manual commands,
	 * otherwise we'd be needlessly feeding the Garbage Collector:
	 * @return
	 */
	public String getResponseString() {
		return _rawStringResponse.toString();
	}

	/**
	 * This class is responsible for retrieving the useful information
	 * contained in the response from the ELM327.
	 * 
	 * @return number of bytes to read from the buffer (== length of respone)
	 */
	public synchronized int getResponsePayload(ByteBuffer response) {
		// Right now we consider that AT commands have no payload
		// (only the String response is used).
		if (!_timedOut && _rawStringResponse != null && !_command.startsWith("AT")) {
			try {
				// Reset the buffer:
				response.rewind();
				
				final int frameLength = ats0_supported? 20 : 28;
				final int initialIndent = ats0_supported? 5 : 7; 
				final int step = ats0_supported? 2 : 3;
				
				// First, remove parasitic '\r':
				//String temp = getResponseString().replaceAll("\\r", ""); // No longer needed thanks to ATL0

				// split("\n") would be easier to read but feeds the GC...
				// each frame should contain exactly 20 char: 3+ 8*2 + "\n"
				int nf=_rawStringResponse.length()/ frameLength; // nf = number of frames
				for (int nl=0; nl<nf; nl++) {           // nl= frame number
					int start=nl*frameLength+initialIndent; // start with the 6th or 8th char
					for(int bb=0;bb<7;bb++){            // bb est le couple à traiter
						int pos = start + bb*step;         // pos est la position dans la chaine du couple à traiter
						response.put((byte) ((Character.digit(_rawStringResponse.charAt(pos), 16) << 4)
											+ Character.digit(_rawStringResponse.charAt(pos+1), 16)));
					}
				}

				// Finally, copy the buffer into a byte array:
				int len = response.position();
				response.rewind();
				return len;
			}
			catch(Throwable e) {
				if (D) Log.e(TAG, "Error that needs debugging! ", e);
			}
		}
		// else:
		return 0;
	}

	public void setDuration(long timeSpent) {
		_duration = timeSpent;
	}
	public long getDuration() {
		return _duration;
	}
	
	public void timedOut(boolean b) {
		_duration = specific_timeout_value;
		_timedOut = b;
		_rawStringResponse.append(UNLUCKY);
	}
	public boolean hasTimedOut() {
		return _timedOut;
	}
	
	public void analyzeResponse() {
		// MOSTLY IMPLEMENTED IN SUBCLASSES !!!
		// Except for Manual Commands:
		notifySender();
	}

	/**
	 * Use this method when re-using a command that has already been sent.
	 */
	public synchronized void reset() {
		_rawStringResponse.setLength(0);
		_duration = 0;
		_notifyMe = null;
		_timedOut = false;
	}
}
