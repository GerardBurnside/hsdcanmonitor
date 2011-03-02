package com.ptc.android.hsdcanmonitor.commands;

import java.nio.ByteBuffer;

import android.util.Log;

import com.ptc.android.hsdcanmonitor.CanInterface;

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
    protected static final boolean D = true;

    protected static final String UNLUCKY = "Timed Out!";

    // Possible specific timeout for a command that might take longer than usual:
    public long specific_timeout_value = CanInterface.DEFAULT_RESPONSE_TIME_OUT;

	protected String _command; // The actual command to send.
	// Buffer for the raw response:
	protected StringBuilder _rawStringResponse = new StringBuilder();
	protected long _duration;
	protected boolean _timedOut = false; // let's not be pessimistic ;-)
	// Optional, to notify the sender that the response is available (for Manual Commands)
	protected Object _notifyMe;
	// In order to reduce garbage collection (lots of cycles!),
	// let's create most of our objects only once:
	private String _tempStringResponse;

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
		if (_tempStringResponse == null) {
			_tempStringResponse = _rawStringResponse.toString();
		}
		return _tempStringResponse;
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
				// First, remove parasitic '\r':
				//String temp = getResponseString().replaceAll("\\r", ""); // No longer needed thanks to ATL0

				// split("\n") would be easier to read but feeds the GC...
				// each frame should contain exactly 20 char: 3+ 8*2 + "\n"
				int nf=_rawStringResponse.length()/ 20; // nf = number of frames
				for (int nl=0; nl<nf; nl++) {           // nl= frame number
					int start=nl*20+5;                  // start with the 6th char
					for(int bb=0;bb<6;bb++){            // bb est le couple à traiter
						int pos = start + bb*2;         // pos est la position dans la chaine du couple à traiter
						response.put((byte) ((Character.digit(_rawStringResponse.charAt(pos), 16) << 4)
											+ Character.digit(getResponseString().charAt(pos+1), 16)));
					}
				}
				/* PREVIOUS CODE:
				// Split the string around '\n' for possible multiframe:
				for (String line : getResponseString().split("\n")) {
					if (line.length() == 0)
						continue;
					//else:
					// Each space-separated string is an hexadecimal value:
					String[] hexaStr = line.split(" ");
					// Convert each two-digit string into the corresponding byte:
					// (ignore the leading 7Ex xx at the beginning of each line)
					for (int k=2; k<hexaStr.length; k++) {
						//response.put(Byte.parseByte(hexaStr[k], 16)); Throws NumberFormatException for values >127
						response.put((byte) ((Character.digit(hexaStr[k].charAt(0), 16) << 4)
	                            + Character.digit(hexaStr[k].charAt(1), 16)));
					}
				}
				***/
				
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

	/**
	 * Use this method when re-using a command that has already been sent.
	 */
	public synchronized void reset() {
		_rawStringResponse.setLength(0);
		_tempStringResponse = null;
		_duration = 0;
		_notifyMe = null;
		_timedOut = false;
	}
}
