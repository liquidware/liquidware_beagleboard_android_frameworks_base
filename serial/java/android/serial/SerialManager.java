/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.serial;

import android.content.Intent;
import android.serial.SerialStatus;
import android.serial.SerialStatus.Listener;
import android.serial.SerialStatus.SerialMsgListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.serial.SerialManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class provides access to the system serial services.  These
 * services allow applications to obtain periodic updates of the
 * device's geographical serial, or to fire an application-specified
 * {@link Intent} when the device enters the proximity of a given
 * geographical serial.
 *
 * <p>You do not
 * instantiate this class directly; instead, retrieve it through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.SERIAL_SERVICE)}.
 */
public class SerialManager {
    private static final String TAG = "SerialManager";
    private ISerialManager mService;
    private final HashMap<SerialStatus.Listener, SerialStatusListenerTransport> mSerialStatusListeners =
        new HashMap<SerialStatus.Listener, SerialStatusListenerTransport>();
    private final HashMap<SerialStatus.SerialMsgListener, SerialStatusListenerTransport> mSerialMsgListeners =
        new HashMap<SerialStatus.SerialMsgListener, SerialStatusListenerTransport>();
    private final SerialStatus mSerialStatus = new SerialStatus();
    private ArrayList<SerialStatusListenerTransport> mListeners = new ArrayList<SerialStatusListenerTransport>();
    //private ArrayList<SerialMsgListener> mSerialMsgListeners = new ArrayList<SerialMsgListener>();
    
    /**
     * @hide - hide this constructor because it has a parameter
     * of type ISerialManager, which is a system private class. The
     * right way to create an instance of this class is using the
     * factory Context.getSystemService.
     */
    public SerialManager(ISerialManager service) {
        Log.d(TAG, "Constructor: service = " + service);
        mService = service;
    }
    
    public boolean begin(String device, int baud) {
    	enable();					//enable the serial thread
    	return native_start(device, baud);	//start the serial engine
    }
 
    public boolean end(String device) {
    	disable();					//enable the serial thread
    	return native_stop();	//start the serial engine
    }
    

    public void print(String msg) {
    	Log.d(TAG, "about to native print");
    	native_serial_print(msg);
    	Log.d(TAG, "finished native print");
    }
    
    // these need to match SerialStatusValue defines in serial.h
    private static final int SERIAL_STATUS_NONE 	  = 0;
    private static final int SERIAL_STATUS_ENGINE_ON  = 1;
    private static final int SERIAL_STATUS_ENGINE_OFF = 2;
    
    private boolean mEngineOn = false;
    
    /**int positionMode, boolean singleFix, int fixInterval
     * called from native code to update our status
     */
    private void reportStatus(int status) {
        Log.v(TAG, "reportStatus status: " + status);
        synchronized(mSerialMsgListeners) {
	        switch (status) {
	            case SERIAL_STATUS_ENGINE_ON:
	                mEngineOn = true;
	                break;
	            case SERIAL_STATUS_ENGINE_OFF:
	                mEngineOn = false;
	                break;
	        }
        }
    }
    
    // This class is used to send SERIAL status events to the client's main thread.
    private class SerialStatusListenerTransport extends ISerialStatusListener.Stub {
    	
        private final SerialStatus.Listener mListener;
        private final SerialStatus.SerialMsgListener mSerialMsgListener;

        // This must not equal any of the SerialStatus event IDs
        private static final int SERIAL_MSG_RECEIVED = 1001;

        private class SerialMsg {
            String mSerialMsg;

            SerialMsg(String msg) {
                mSerialMsg = msg;
            }
        }
        private ArrayList<SerialMsg> mSerialMsgBuffer;
        
        SerialStatusListenerTransport(SerialStatus.Listener listener) {
        	Log.v(TAG, "Constructor: SerialStatusListenerTransport Listener");
            mListener = listener;
            mSerialMsgListener = null;
            mSerialMsgBuffer = null;
        }
        
        SerialStatusListenerTransport(SerialStatus.SerialMsgListener listener) {
        	Log.v(TAG, "Constructor: SerialStatusListenerTransport SerialMsgListener");
        	mListener = null;
        	mSerialMsgListener = listener;
            mSerialMsgBuffer = new ArrayList<SerialMsg>();
        }

        public void onSerialStarted() {
            if (mListener != null) {
                Message msg = Message.obtain();
                msg.what = SerialStatus.SERIAL_EVENT_STARTED;
                mSerialHandler.sendMessage(msg);
            }
        }

        public void onSerialStopped() {
            if (mListener != null) {
                Message msg = Message.obtain();
                msg.what = SerialStatus.SERIAL_EVENT_STOPPED;
                mSerialHandler.sendMessage(msg);
            }
        }
        
        public void onSerialMsgReceived(String sMsg) {
        	Log.v(TAG, "SerialStatusListenerTransport: onSerialMsgReceived got '" + sMsg + "'");
            if (mSerialMsgListener != null) {
            	Log.v(TAG, "mSerialMsgListener not null");
                synchronized (mSerialMsgBuffer) {
                	Log.v(TAG, "adding new SerialMsg");
                    mSerialMsgBuffer.add(new SerialMsg(sMsg));
                    Log.v(TAG, "done adding SerialMsg");
                }
                
                Message msg = Message.obtain();
                msg.what = SERIAL_MSG_RECEIVED;
                Log.v(TAG, "msg.what=SERIAL_MSG_RECEIVED");
                // remove any SERIAL_MSG_RECEIVED messages already in the queue
                mSerialHandler.removeMessages(SERIAL_MSG_RECEIVED);
                Log.v(TAG, "removed SERIAL_MSG_RECEIVED messages already in the queue, sending");
                mSerialHandler.sendMessage(msg);
                Log.v(TAG, "message sent");
            }
        }

        private final Handler mSerialHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
            	Log.v(TAG, "mSerialHandler: handling message");
            	if (msg.what == SERIAL_MSG_RECEIVED) {
            		Log.v(TAG, "mSerialHandler: message is SERIAL_MSG_RECEIVED");
                    synchronized (mSerialMsgBuffer) {
                        int length = mSerialMsgBuffer.size();
                        Log.v(TAG, "mSerialHandler: Sending Messages to App, buffer size=" + length);
                        for (int i = 0; i < length; i++) {
                            SerialMsg sMsg = mSerialMsgBuffer.get(i);
                            Log.v(TAG, "mSerialHandler: about to send '" + sMsg + "' to mSerialMsgListener=" + mSerialMsgListener);
                            mSerialMsgListener.onSerialMsgReceived(sMsg.mSerialMsg);
                            Log.v(TAG, "mSerialHandler: complete");
                        }
                        mSerialMsgBuffer.clear();
                    }
                } else {
                	Log.v(TAG, "mSerialHandler: msg.what=" + msg.what);
                    // synchronize on mSerialStatus to ensure the data is copied atomically.
                    synchronized(mSerialStatus) {
                        mListener.onSerialStatusChanged(msg.what);
                    }
                }
            }
        };
    }

    /**
     * Adds a SERIAL status listener.
     *
     * @param listener SERIAL status listener object to register
     *
     * @return true if the listener was successfully added
     * 
     */
    public boolean addSerialStatusListener(SerialStatus.Listener listener) {
        boolean result;

        if (mSerialStatusListeners.get(listener) != null) {
            // listener is already registered
            return true;
        }
        try {
            SerialStatusListenerTransport transport = new SerialStatusListenerTransport(listener);
            result = mService.addSerialStatusListener(transport);
            if (result) {
                mSerialStatusListeners.put(listener, transport);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in registerSerialStatusListener: ", e);
            result = false;
        }

        return result;
    }

    /**
     * Removes a SERIAL status listener.
     *
     * @param listener SERIAL status listener object to remove
     */
    public void removeSerialStatusListener(SerialStatus.Listener listener) {
        try {
            SerialStatusListenerTransport transport = mSerialStatusListeners.remove(listener);
            if (transport != null) {
                mService.removeSerialStatusListener(transport);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in unregisterSerialStatusListener: ", e);
        }
    }
   
    /**
     * Adds an SerialMsgListener.
     *
     * @param listener a {#link SerialStatus.SerialMsgListener} object to register
     *
     * @return true if the listener was successfully added
     *
     */
    public boolean addSerialMsgListener(SerialStatus.SerialMsgListener listener) {
        boolean result = false;
        
        Log.v(TAG, "Attemping to add new SerialMsgListener");
        
        if (mSerialMsgListeners.get(listener) != null) {
            // listener is already registered
        	Log.v(TAG, "SerialMsgListener is already added");
            return true;
        }
        try {
        	Log.v(TAG, "mListeners=" + mListeners.toString());
        	Log.v(TAG, "Creating new SerialMsgListener transport with listener=" + listener.toString());
            SerialStatusListenerTransport transport = new SerialStatusListenerTransport(listener);
            Log.v(TAG, "transport=" + transport.toString());
            Log.v(TAG, "adding transport to mService=" + mService.toString());
            result = mService.addSerialStatusListener(transport);
        	Log.v(TAG, "Adding to mListeners transport=" + transport.toString());
        	mListeners.add(transport);
            Log.v(TAG, "added transport with result=" + result);
            if (result) {
            	Log.v(TAG, "putting listener, transport into mSerialMsgListeners");
                mSerialMsgListeners.put(listener, transport);
                Log.v(TAG, "mSerialMsgListeners.size=" + mSerialMsgListeners.size());
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in registerSerialStatusListener: ", e);
            result = false;
        }

        return result;
    }

    /**
     * Removes an NMEA listener.
     *
     * @param listener a {#link SerialStatus.NmeaListener} object to remove
     */
    public void removeSerialMsgListener(SerialStatus.SerialMsgListener listener) {
        try {
            SerialStatusListenerTransport transport = mSerialMsgListeners.remove(listener);
            if (transport != null) {
                mService.removeSerialStatusListener(transport);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in unregisterSerialStatusListener: ", e);
        }
    }
    
    /**
     * Retrieves information about the current status of the SERIAL engine.
     * This should only be called from the {@link SerialStatus.Listener#onSerialStatusChanged}
     * callback to ensure that the data is copied atomically.
     *
     * The caller may either pass in a {@link SerialStatus} object to set with the latest
     * status information, or pass null to create a new {@link SerialStatus} object.
     *
     * @param status object containing SERIAL status details, or null.
     * @return status object containing updated SERIAL status.
     */
    public SerialStatus getSerialStatus(SerialStatus status) {
        if (status == null) {
            status = new SerialStatus();
       }
       status.setStatus(mSerialStatus);
       return status;
    }
    
    /**
     * called from native code to report SERIAL data received
     */
    private void reportSerialMsg() {

        synchronized(mSerialMsgListeners) {
            int size = mSerialMsgListeners.size();
            Log.v(TAG, "reportSerialMsg: with mSerialMsgListeners = " + size); 
            if (size > 0) {
                // don't bother creating the String if we have no listeners
                int length = native_read_serial_msg(mSerialMsgBuffer, mSerialMsgBuffer.length);
                String sMsg = new String(mSerialMsgBuffer, 0, length);

                for (int i = 0; i < size; i++) {
                	//Listener listener;
                	SerialMsgListener listener = null;
                    try {
                    	Log.v(TAG, "mService=" + this.toString());
                    	Log.v(TAG, "mListeners=" + mListeners.toString());
                    	
                    	Log.v(TAG, "Getting SerialStatusListenerTransport=" + mListeners.get(i).toString());
                    	SerialStatusListenerTransport transport = mListeners.get(i);
                    	Log.v(TAG, "transport sending='" + sMsg + "'");
                    	transport.onSerialMsgReceived(sMsg);
                    	

                    } catch (Exception e) {
                        Log.w(TAG, "RemoteException in reportSerialMsg e=" + e);
                        if (listener != null) {
                        	mSerialMsgListeners.remove(listener);
                        	// adjust for size of list changing
                        	size--;
                        }
                    }
                }
            }
        }
    }
    
    // true if we are enabled
    private boolean mEnabled;
    private SerialEventThread mEventThread;
    
    /**
     * Enables this provider.  When enabled, calls to getStatus()
     * must be handled.  Hardware may be started up
     * when the provider is enabled.
     */
    public synchronized void enable() {
        Log.d(TAG, "enable");
        if (mEnabled) return;
        mEnabled = native_init();

        if (mEnabled) {
            // run event listener thread while we are enabled
            mEventThread = new SerialEventThread();
            mEventThread.start();
        } else {
            Log.w(TAG, "Failed to enable serial provider");
        }
    }
    
    public synchronized void disable() {
        Log.d(TAG, "disable");
        if (!mEnabled) return;

        mEnabled = false;
        native_disable();
        
        // make sure our event thread exits
        if (mEventThread != null) {
            try {
                mEventThread.join();
            } catch (InterruptedException e) {
                Log.w(TAG, "InterruptedException when joining mEventThread");
            }
            mEventThread = null;
        }

        // do this before releasing wakelock
        native_cleanup();
    }
    
    private class SerialEventThread extends Thread {

        public SerialEventThread() {
            super("SerialEventThread");
        }

        public void run() {
            Log.d(TAG, "SerialEventThread starting");
            // Exit as soon as disable() is called instead of waiting for the Serial to stop.
            while (mEnabled) {
                // this will wait for an event from the Serial,
                // which will be reported via reportStatus
                native_wait_for_event();
            }
            Log.d(TAG, "SerialEventThread exiting");
        }
    }
    
    // preallocated to avoid memory allocation in reportSerialMsg()
    private byte[] mSerialMsgBuffer = new byte[1024];
    
    static { class_init_native(); }
    private static native void class_init_native();
    private static native boolean native_is_supported();

    private native boolean native_init();
    private native void native_disable();
    private native void native_cleanup();
    private native boolean native_start(String device, int baud);
    private native boolean native_stop();
    private native void native_wait_for_event();
    
    private native int native_read_serial_msg(byte[] buffer, int bufferSize);
    private native void native_serial_print(String msg);
}
