package com.ptc.hsdcanmonitor.commands;

import java.util.ArrayList;

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
	protected String _command;
	protected byte[] _response;
	protected long _duration;
	protected boolean _timedOut = false; // let's not be pessimistic ;-)
	// Optional, to notify the sender that the response is available.
	protected Object _notifyMe;

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

	public void setRawResponse(ArrayList<Byte> resp) {
		_response = new byte[resp.size()];
		for (int i = 0; i < resp.size(); i++) {
			_response[i] = resp.get(i);
		}
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
		if (_response != null)
			return new String(_response);
		else return null;
	}

	public byte[] getResponsePayload() {
		if (_response != null) {
			// TODO: only keep the useful bytes !!!
			return _response;
		}
		else return null;
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
		byte[] unlucky = "Timed Out!".getBytes();
		if (_response == null) {
			_response = unlucky;
		}
		else {
			byte[] partial = _response;
			_response = new byte[partial.length+unlucky.length];
			for (int k=0; k<partial.length+unlucky.length; k++)
			{ // _response = partial + unlucky:
				_response[k] = k<partial.length?
									partial[k]
									: unlucky[k-partial.length];
			}
		}
	}
	public boolean hasTimedOut() {
		return _timedOut;
	}

	/**
	 * Use this method when re-using a command that has already been sent.
	 */
	public void reset() {
		_response = null;
		_duration = 0;
		_notifyMe = null;
		_timedOut = false;
	}
}
