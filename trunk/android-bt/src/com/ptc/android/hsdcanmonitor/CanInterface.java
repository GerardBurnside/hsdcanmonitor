package com.ptc.android.hsdcanmonitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ptc.android.hsdcanmonitor.commands.BackgroundCommand;
import com.ptc.android.hsdcanmonitor.commands.CommandResponseObject;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * 
 * This class is responsible for the walkie-talkie interface
 * with the Bluetooth canbus device.
 * It reads on a blocking queue for a command to send and
 * then waits for the prompt char ('>') to be returned by the BT device.
 * 
 * @author Guinness
 *
 */
public class CanInterface implements Runnable {

    // Debugging
    private static final String TAG = "HsdCanMonitor";
    private static final boolean D = true;
    // As suggested by Android's Javadoc, use this specific value:
	protected static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	// The maximum amount of time that we are willing to wait for
	// a single command response (in milliseconds):
	public static final long DEFAULT_RESPONSE_TIME_OUT = 400;
	// The input of this thread is a 1-bounded blocking queue,
	// i.e. we are waiting for each command, one at a time.
	protected BlockingQueue<CommandResponseObject> _inputQueue;
	// The output of the this thread is an unbounded blocking queue (so that the
	// thread reading it is naturally blocked if there is nothing to read).
	protected BlockingQueue<CommandResponseObject> _outputQueue;
	protected volatile boolean _keepRunning = true;
	// Temporary buffer to collect the whole response until '>' is received:
	//protected ArrayList<Byte> _buff = new ArrayList<Byte>();
	protected CharBuffer _buff = CharBuffer.allocate(1024); // might be increased if needed.
	protected char[] _result = new char[1024];

	// Socket connection to the bluetooth device:
	protected BluetoothSocket _sock = null;
	// inbound stream from the bluetooth device:
	protected InputStream _in = null;
	// outbound stream to the bluetooth device: 
	protected OutputStream _out = null;
	// Because we want to be able to wait for at most MAX_COMMAND_RESPONSE_TIME,
	// we need to use a separate Thread that can be interrupted if over MAX_COMMAND_RESPONSE_TIME.
	protected ExecutorService _commandsProcessor = Executors.newSingleThreadExecutor();
	// We process the requests one at a time:
	protected CommandResponseObject _currentRequest;
	// Dropping the next background command because of failure of current one:
	protected boolean _dropNextBackgroundCommand = false;

	// Used to debug when no BT CAN interface is available for tests:
	private boolean _fakeDebugResponses = false;// true if no BT dongle available ;-)
	// For random chance timeout when debugging:
	private Random _myRand = new Random();


	// Private constructor prevents instantiation from other classes:
	// We want to enforce the walkie-talkie interface by being the only instance and with a single thread !
	private CanInterface() {
		// The scheduler will give us the commands one by one.
		_inputQueue = new ArrayBlockingQueue<CommandResponseObject>(1);
		_outputQueue = new LinkedBlockingQueue<CommandResponseObject>(); // unbounded
	}

	/**
	* Singleton implementation:
	* CanInterfaceHolder is loaded on the first execution of CanInterface.getInstance() 
	* or the first access to CanInterfaceHolder.INSTANCE, not before.
	*/
	private static class CanInterfaceHolder { 
		public static final CanInterface INSTANCE = new CanInterface();
	}
	 
	public static CanInterface getInstance() {
		return CanInterfaceHolder.INSTANCE;
	} 
	
	public BlockingQueue<CommandResponseObject> getInputQueue() {
		return _inputQueue;
	}
	
	public BlockingQueue<CommandResponseObject> getOutputQueue() {
		return _outputQueue;
	}
	
	public void stop() {
		// Stop the CanInterface thread and reset the streams,
		// so that a connectToDevice() and new Thread().start()
		// are required to restart the commands processing.

		// TODO? send a request for the device to go into sleep mode before closing the socket?
		_keepRunning = false;
		try {
			if (_sock != null)
				_sock.close();
		} catch (Throwable e1) {
			if (D) Log.e(TAG, "Error while trying to close BT socket!",e1);
		}
		_sock = null;
		_in = null;
		_out = null;
		CoreEngine.setState(CoreEngine.STATE_NONE);
	}

	/**
	 * This is the worker code for sending a command and waiting for the response (until '>');
	 * returns true if success.
	 */
	private Callable<Boolean> _singleCommandExecution = new Callable<Boolean>() {
		@Override
		public Boolean call() {
			try {
				char c = 0; // I think we can ignore encoding issues - need to verify...
				_buff.clear();
				long before = System.currentTimeMillis();
				////////////////// DEBUG ONLY ////////////////////
				if (_fakeDebugResponses) { // ONLY FOR EARLY DEBUGGING:
					final String debugStr = //"Fake response!>"
						"7E8102E610100000000\n7E82114655061530000\n7E822000000002A7B2A\n7E823FF6712A7253729\n7E8243C000000008049\n7E825BB8A7FFF000000\n7E826000008510A0000\n\n >";
					int indexDebug=0;
					// timeout if random is true four times in a row!
					if (_myRand.nextBoolean() && _myRand.nextBoolean()
							&& _myRand.nextBoolean() && _myRand.nextBoolean()) {
						_buff.put(debugStr.charAt(0)).put(debugStr.charAt(1));
						_buff.put(debugStr.charAt(2)).put(debugStr.charAt(3));
						_buff.put(debugStr.charAt(4));
						try {
							if (D) Log.d(TAG, "Simulating TimeOut, sleep(600)...");
							Thread.sleep(600);
						} catch (InterruptedException e) {
							_buff.put(debugStr.charAt(5));
							if (D) Log.d(TAG, "Sleep(600) interrupted!");
							return true;
						}
						_buff.put(debugStr.charAt(6)); // Should not happen ??
						if (D) Log.d(TAG, "After the sleep(600)!?!");
					} // Not a timeout:
					else {
						// Simulating delay before response:
						long delay = 20 +(Math.abs(_myRand.nextInt()) % 50); // Max 70ms: let's simulate a fast interface!
						try {
							if (D) Log.d(TAG, "Simulating Delay: "+delay+"ms...");
							Thread.sleep(delay);
						} catch (InterruptedException e) {
							// Ignore..
						}
						while ((c = debugStr.charAt(indexDebug++)) != '>') {
							_buff.put(c);
						}
					}
					//if (D) Log.d(TAG, "After fake debug response.");
				}
				////////////// END DEBUG ONLY ////////////////////
				else { // this is the actual running code when not debugging: 
					// Start by sending the command:
					_out.write(_currentRequest.getCommandToSend());
					_out.flush();
					// Then wait for the end of the response (i.e. the prompt char '>'):
					while ((c = (char)_in.read()) != '>') {
						_buff.put(c);
					}
				}
				// How long did it take to receive the full response:
				_currentRequest.setDuration(System.currentTimeMillis() - before);
				// Set the response:
				setResponseData();
			} catch (IOException e) {
				// Is this a timeOut?
				if (e instanceof InterruptedIOException)
					return true; // Handled by parent thread.
				else return false;
			}
			return true;
		}
	};
	
	protected void setResponseData() {
		int len = _buff.position();
		_buff.rewind();
		_buff.get(_result, 0, len);
		_currentRequest.setRawResponse(_result,len);
	}
	
	/**
	 * This method is what the Interface thread keeps doing,
	 * i.e. reading a command to send and wait for the response.
	 * Note that the thread ends if we get disconnected (to save battery usage)!
	 */
	public void run() {
		while (_keepRunning) {
			try {
				handleCommand(_inputQueue.take());
			} catch (InterruptedException ex) {
				// TODO?
			}
		}
	}
	
	private void handleCommand(CommandResponseObject request) {
		try {
			// Use a Callable in a separate thread so that we can wait at most MAX_COMMAND_RESPONSE_TIME:
			_currentRequest = request;
			if (_dropNextBackgroundCommand) {
				_dropNextBackgroundCommand = false;
				if (_currentRequest instanceof BackgroundCommand)
					return; // Simply ignore it!
			}
			Future<Boolean> result = _commandsProcessor.submit(_singleCommandExecution);
			try {
				Boolean success = result.get(request.specific_timeout_value, TimeUnit.MILLISECONDS);
				if (!success) {
					handleError();
				}
			}
			catch (TimeoutException toe) {
				// Let's put what we received so far in the response and tag it as timed out:
				setResponseData();
				_currentRequest.timedOut(true);
				if (D) Log.d(TAG, "TimeOut: interrupting 'future' result...");
				result.cancel(true); // Try to force interrupt.
			}
		} catch (Throwable e) {
			if (D) Log.e(TAG, "Error while reading response...",e);
			//handleError();
		}
		// Finally, send the response to the ResponseHandler Thread:
		_outputQueue.add(_currentRequest);
		// Post-processing to check if we need to skip a command already in the loop:
		if (_currentRequest instanceof BackgroundCommand) {
			BackgroundCommand bkgCmd = (BackgroundCommand) _currentRequest;
			if (bkgCmd.resetOnFailure && bkgCmd.hasTimedOut())
				_dropNextBackgroundCommand = true;
		}
	}
	
	private void handleError() {
		// Error handling for now:
		stop();
		// Notify a possible sender that the command failed:
		_currentRequest.timedOut(true); // Maybe a separate flag might be more appropriate..
		_currentRequest.notifySender();
	}

	/**
	 * Returns true if connect succeeded, false otherwise.
	 */
	public boolean connectToDevice(BluetoothDevice device) {
		if (!_fakeDebugResponses) {
			try {
				_sock = device.createRfcommSocketToServiceRecord(MY_UUID);
		        _sock.connect();
		        _in = _sock.getInputStream();
		        _out = _sock.getOutputStream();
			} catch (IOException e) {
				CoreEngine.setState(CoreEngine.STATE_NONE);
				_keepRunning = false;
				return false;
			}
		} // else fake that the connection succeeded !

		// Another thread might later tell us to stop!
		_keepRunning = true;
		return true;
	}

	public boolean isConnected() {
		// TODO improve this?
		return _keepRunning && (_fakeDebugResponses || _out != null);
	}

}
