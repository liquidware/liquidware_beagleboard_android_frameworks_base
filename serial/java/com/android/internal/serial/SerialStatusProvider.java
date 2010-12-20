/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.serial;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.SntpClient;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Config;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.Map.Entry;

import android.serial.*;

/**
 * A SERIAL implementation of SerialStatusProvider used by SerialManager.
 *
 * {@hide}
 */
public class SerialStatusProvider extends ISerialStatusProvider.Stub {

    private static final String TAG = "SerialStatusProvider";
    
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();
    private final SparseIntArray mClientUids = new SparseIntArray();
    private final Context mContext;
    private final ISerialManager mSerialManager;
    
    public SerialStatusProvider(Context context, ISerialManager serialManager) {
        mContext = context;
        mSerialManager = serialManager;
        Log.v(TAG, "Constructed SerialStatusProvider");
    }
    
    private final class Listener implements IBinder.DeathRecipient {
        final ISerialStatusListener mListener;
        
        int mSensors = 0;
        
        Listener(ISerialStatusListener listener) {
            mListener = listener;
        }
        
        public void binderDied() {
            if (Config.LOGD) Log.d(TAG, "Serial status listener died");

            synchronized(mListeners) {
                mListeners.remove(this);
            }
            if (mListener != null) {
                mListener.asBinder().unlinkToDeath(this, 0);
            }
        }
    }

    public void addListener(int uid) {
        synchronized(mListeners) {
            if (mClientUids.indexOfKey(uid) >= 0) {
                // Shouldn't be here -- already have this uid.
                Log.w(TAG, "Duplicate add listener for uid " + uid);
                return;
            }
            mClientUids.put(uid, 0);
        }
    }

    public void removeListener(int uid) {
        synchronized(mListeners) {
            if (mClientUids.indexOfKey(uid) < 0) {
                // Shouldn't be here -- don't have this uid.
                Log.w(TAG, "Unneeded remove listener for uid " + uid);
                return;
            }
            mClientUids.delete(uid);
        }
    }
    
    public void addSerialStatusListener(ISerialStatusListener listener) throws RemoteException {
        if (listener == null) {
            throw new NullPointerException("listener is null in addSerialStatusListener");
        }

        synchronized(mListeners) {
            IBinder binder = listener.asBinder();
            int size = mListeners.size();
            for (int i = 0; i < size; i++) {
                Listener test = mListeners.get(i);
                if (binder.equals(test.mListener.asBinder())) {
                    // listener already added
                    return;
                }
            }

            Listener l = new Listener(listener);
            binder.linkToDeath(l, 0);
            mListeners.add(l);
        }
    }

    public void removeSerialStatusListener(ISerialStatusListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener is null in addSerialStatusListener");
        }

        synchronized(mListeners) {
            IBinder binder = listener.asBinder();
            Listener l = null;
            int size = mListeners.size();
            for (int i = 0; i < size && l == null; i++) {
                Listener test = mListeners.get(i);
                if (binder.equals(test.mListener.asBinder())) {
                    l = test;
                }
            }

            if (l != null) {
                mListeners.remove(l);
                binder.unlinkToDeath(l, 0);
            }
        }
    }

}
