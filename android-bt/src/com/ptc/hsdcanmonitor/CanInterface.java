package com.ptc.hsdcanmonitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
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

import com.ptc.hsdcanmonitor.commands.BackgroundCommand;
import com.ptc.hsdcanmonitor.commands.CommandResponseObject;

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
    // Is this specific value important?
	protected static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	// The maximum amount of time that we are willing to wait for
	// a single command response (in milliseconds):
	public static final long MAX_COMMAND_RESPONSE_TIME = 400;
	// The input of this thread is a 1-bounded blocking queue,
	// i.e. we are waiting for each command, one at a time.
	protected BlockingQueue<CommandResponseObject> _inputQueue;
	// The output of the this thread is an unbounded blocking queue (so that the
	// thread reading it is naturally blocked if there is nothing to read).
	protected BlockingQueue<CommandResponseObject> _outputQueue;
	protected volatile boolean _keepRunning = true;
	// Temporary buffer to collect the whole response until '>' is received:
	protected ArrayList<Byte> _buff = new ArrayList<Byte>();
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
	private boolean _fakeDebugResponses = false;// true;
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
		_keepRunning = false;
	}

	/**
	 * This is the worker code for sending a command and waiting for the response (until '>');
	 * returns true if success.
	 */
	private Callable<Boolean> _singleCommandExecution = new Callable<Boolean>() {
		@SuppressWarnings("unchecked")
		@Override
		public Boolean call() {
			try {
				byte c = 0;
				_buff.clear();
				long before = System.currentTimeMillis();
				////////////////// DEBUG ONLY ////////////////////
				if (_fakeDebugResponses) { // ONLY FOR EARLY DEBUGGING:
					byte[] debug = "Fake response!>".getBytes();
					int indexDebug=0;
					// timeout if random true three times in a row!
					if (_myRand.nextBoolean() && _myRand.nextBoolean() && _myRand.nextBoolean()) {
						_buff.add(debug[0]);_buff.add(debug[1]);
						_buff.add(debug[2]);_buff.add(debug[3]);
						_buff.add(debug[4]);
						try {
							if (D) Log.d(TAG, "Simulating TimeOut, sleep(600)...");
							Thread.sleep(600);
						} catch (InterruptedException e) {
							_buff.add(debug[5]);
							if (D) Log.d(TAG, "Sleep(600) interrupted!");
							return true;
						}
						_buff.add(debug[6]); // Should not happen ??
						if (D) Log.d(TAG, "After the sleep(600)!?!");
					} // Not a timeout:
					else while ((char)(c = debug[indexDebug++]) != '>') {
						_buff.add(c);
					}
					if (D) Log.d(TAG, "After fake debug response.");
				}
				////////////// END DEBUG ONLY ////////////////////
				else { // this is the actual running code when not debugging: 
					// Start by sending the command:
					_out.write(_currentRequest.getCommandToSend());
					_out.flush();
					// Then wait for the end of the response (i.e. the prompt char '>'):
					while ((char)(c = (byte)_in.read()) != '>') {
						_buff.add(c);
					}
				}
				// How long did it take to receive the full response:
				_currentRequest.setDuration(System.currentTimeMillis() - before);
				// Store the response:
				_currentRequest.setRawResponse((ArrayList<Byte>)_buff.clone());
			} catch (IOException e) {
				// Is this a timeOut?
				if (e instanceof InterruptedIOException)
					return true; // Handled by parent thread.
				else return false;
			}
			return true;
		}
	};
	
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
	
	@SuppressWarnings("unchecked")
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
				Boolean success = result.get(MAX_COMMAND_RESPONSE_TIME, TimeUnit.MILLISECONDS);
				if (!success) {
					handleError();
				}
			}
			catch (TimeoutException toe) {
				// Let's put what we received so far in the response and tag it as timed out:
				_currentRequest.setRawResponse((ArrayList<Byte>)_buff.clone());
				_currentRequest.timedOut(true);
				if (D) Log.d(TAG, "TimeOut: interrupting 'future' result...");
				result.cancel(true); // Try to force interrupt.
			}
		} catch (Throwable e) {
			handleError();
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
		// Error handling:
		// For now we stop the CanInterface thread and reset the streams,
		// so that a connectToDevice() and new Thread().start()
		// are required to restart the commands processing.
		_keepRunning = false;
		try {
			_sock.close();
		} catch (Throwable e1) {
			// Ignore.
		}
		_in = null;
		_out = null;
		CoreEngine.getInstance().setState(CoreEngine.STATE_NONE);
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
				CoreEngine.getInstance().setState(CoreEngine.STATE_NONE);
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
