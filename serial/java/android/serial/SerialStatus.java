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

package android.serial;

import android.serial.SerialManager;

/**
 * This class represents the current state of the SERIAL engine.
 * This class is used in conjunction with the {@link Listener} interface.
 */
public final class SerialStatus {

    /* These package private values are modified by the SerialManager class */

    /**
     * Event sent when the SERIAL system has started.
     */
    public static final int SERIAL_EVENT_STARTED = 1;

    /**
     * Event sent when the SERIAL system has stopped.
     */
    public static final int SERIAL_EVENT_STOPPED = 2;

    public interface Listener {
        /**
         * @param event event number for this notification
         */
        void onSerialStatusChanged(int event);
    }

    
    public interface SerialMsgListener {
        void onSerialMsgReceived(String sMsg);
    }

    SerialStatus() {
    }

    void setStatus(SerialStatus status) {
    	
    }
}
