package com.ptc.hsdcanmonitor.commands;

import java.nio.ByteBuffer;

import android.util.Log;

import com.ptc.hsdcanmonitor.CanInterface;

/**
 * 
 * This class is used to pass a command to the CanInterface
 * and store the result of this command.
 * 
 * @author guinness
 *
 */
public class CommandResponseObject {
    // Debugging
    private static final String TAG = "HsdCanMonitor";
    private static final boolean D = true;

    protected static final String UNLUCKY = "Timed Out!";
	protected String _command;
	protected StringBuilder _rawStringResponse = new StringBuilder();
	protected long _duration;
	protected boolean _timedOut = false; // let's not be pessimistic ;-)
	// Optional, to notify the sender that the response is available.
	protected Object _notifyMe;
	// TODO In order to reduce garbage collection (lots of cycles!),
	// let's create most of our objects only once:
	private String _tempStringResponse;
	protected ByteBuffer _formattedBytesResponse = ByteBuffer.allocate(1024);

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
	 * @return a byte[] => to decrease garbage collector's work, we
	 * might consider using a ByteBuffer provided by the caller instead)
	 * e.g. => void getResponsePayload(ByteBuffer response)
	 */
	public synchronized byte[] getResponsePayload() {
		// Right now we consider that AT commands have no payload
		// (only the String response is used).
		if (!_timedOut && _rawStringResponse != null && !_command.startsWith("AT")) {
			try {
				// First, remove parasitic '\r':
				String temp = getResponseString().replaceAll("\\r", ""); // TODO: Remove empty lines?
				// Split the string around '\n' for possible multiframe:
				boolean firstLine = true;
				int offset = 1; // First byte (7E8, 7EA etc.) is ignored.
				for (String line : temp.split("\n")) {
					if (line.length() == 0)
						continue;
					//else:
					// Each space-separated string is an hexadecimal value:
					String[] hexaStr = line.split(" ");
					if (firstLine) {
						firstLine = false;
						if ("10".equals(hexaStr[1])) {
							++offset; // Let's ignore also the second byte.
						}
					}
					// Convert each two-digit string into the corresponding byte:
					for (int k=offset; k<hexaStr.length; k++) {
						//_formattedBytesResponse.put(Byte.parseByte(hexaStr[k], 16)); Throws NumberFormatException for values >127
						_formattedBytesResponse.put((byte) ((Character.digit(hexaStr[k].charAt(0), 16) << 4)
	                            + Character.digit(hexaStr[k].charAt(1), 16)));
					}
				}
				
				// Finally, copy the buffer into a byte array:
				int len = _formattedBytesResponse.position();
				byte[] res = new byte[len];
				_formattedBytesResponse.rewind();
				_formattedBytesResponse.get(res);
				return res;
			}
			catch(Throwable e) {
				if (D) Log.e(TAG, "Error that needs debugging! ", e);
			}
		}
		// else:
		return null;
	}

	public void setDuration(long timeSpent) {
		_duration = timeSpent;
	}
	public long getDuration() {
		return _duration;
	}
	
	public void timedOut(boolean b) {
		_duration = CanInterface.MAX_COMMAND_RESPONSE_TIME;
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
		_formattedBytesResponse.rewind(); // In case an exception occurred...
		_duration = 0;
		_notifyMe = null;
		_timedOut = false;
	}
}
