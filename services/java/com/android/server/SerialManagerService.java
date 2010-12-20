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

package com.android.server;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import com.android.internal.serial.SerialStatusProvider;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.serial.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.util.PrintWriterPrinter;

import android.serial.ISerialStatusProvider;
import android.serial.ISerialStatusListener;
import android.serial.ISerialManager;

/**
 * The service class that manages SerialProviders and issues serial
 * updates and alerts.
 *
 * {@hide}
 */
public class SerialManagerService extends ISerialManager.Stub implements Runnable {
    private static final String TAG = "SerialManagerService";
    
    private static boolean sProvidersLoaded = false;
    
    private final Context mContext;
    private ISerialStatusProvider mSerialStatusProvider;

    // wakelock variables
    private int mPendingBroadcasts;
    private SerialWorkerHandler mSerialHandler;
    
    /**
     * Object used internally for synchronization
     */
    private final Object mLock = new Object();
    
    /**
     * List of all receivers.
     */
    //private final HashMap<Object, Receiver> mReceivers = new HashMap<Object, Receiver>();


    /**
     * List of serial providers.
     */
//    private final ArrayList<SerialProviderProxy> mProviders =
//        new ArrayList<SerialProviderProxy>();
//    private final HashMap<String, SerialProviderProxy> mProvidersByName
//        = new HashMap<String, SerialProviderProxy>();

    /**
     * @param context the context that the SerialManagerService runs in
     */
    public SerialManagerService(Context context) {
        super();
        mContext = context;
        
        Log.v(TAG, "Starting new thread");
        Thread thread = new Thread(null, this, "SerialManagerService");
        thread.start();
        
        Log.v(TAG, "Constructed SerialManager Service");
    }
    
    private void loadProviders() {
        synchronized (mLock) {
            if (sProvidersLoaded) {
                return;
            }

            // Load providers
            loadProvidersLocked();
            sProvidersLoaded = true;
        }
    }

    private void loadProvidersLocked() {
        try {
            _loadProvidersLocked();
        } catch (Exception e) {
            Log.e(TAG, "Exception loading providers:", e);
        }
    }
    
    private void _loadProvidersLocked() {
    	Log.v(TAG, "attempting to initialize new SerialStatusProvider");
        SerialStatusProvider provider = new SerialStatusProvider(mContext, this);
        mSerialStatusProvider = provider;
        
    }
    
    private void initialize() {
        // Load providers
        loadProviders();
    }

    public void run()
    {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        Looper.prepare();
        mSerialHandler = new SerialWorkerHandler();
        initialize();
        Looper.loop();
    }
    
    public boolean addSerialStatusListener(ISerialStatusListener listener) {
    	Log.v(TAG, "Attempting to add ISerialStatusListener");
        if (mSerialStatusProvider == null) {
        	Log.v(TAG, "not added, mSerialStatusProvider is null");
            return false;
        }
        try {
        	Log.v(TAG, "attempting to add listener to mSerialStatusProvider");
            mSerialStatusProvider.addSerialStatusListener(listener);
            Log.v(TAG, "success adding listener");
        } catch (RemoteException e) {
            Log.e(TAG, "mSerialStatusProvider.addSerialStatusListener failed", e);
            return false;
        }
        return true;
    }

    public void removeSerialStatusListener(ISerialStatusListener listener) {
    	synchronized (mLock) {
	        try {
	            mSerialStatusProvider.removeSerialStatusListener(listener);
	        } catch (Exception e) {
	            Log.e(TAG, "mSerialStatusProvider.removeSerialStatusListener failed", e);
	        }
    	}
    }
    
    private class SerialWorkerHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            try {
            	Log.v(TAG, "SerialWorkerHandler: got message msg.what=" + msg.what);
            } catch (Exception e) {
                // Log, don't crash!
                Log.e(TAG, "Exception in LocationWorkerHandler.handleMessage:", e);
            }
        }
    }

	public void onSendFinished(PendingIntent pendingIntent, Intent intent,
			int resultCode, String resultData, Bundle resultExtras) {
		// TODO Auto-generated method stub
		
	}
}
