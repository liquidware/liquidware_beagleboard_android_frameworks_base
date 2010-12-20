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

#define LOG_TAG "SerialManager-JNI"

//#define LOG_NDDEBUG 0

#include "JNIHelp.h"
#include "jni.h"
#include "hardware_legacy/serial.h"
#include "utils/Log.h"
#include "utils/misc.h"

#include <string.h>
#include <pthread.h>

static pthread_mutex_t sEventMutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t sEventCond = PTHREAD_COND_INITIALIZER;
static jmethodID method_reportStatus;
static jmethodID method_reportSerialMsg;

static const SerialInterface* sSerialInterface = NULL;

// data written to by SERIAL callbacks
static SerialStatus    sSerialStatus;
static SerialMsg	sSerialReceive;

// a copy of the data shared by android_hardware_SerialManager_wait_for_event
// and android_hardware_SerialManager_read_status
static SerialStatus    sSerialStatusCopy;
static SerialMsg	sSerialReceiveCopy;

enum CallbackType {
    kStatus 			 = 1,
    kDisableRequest 	 = 16,
    kSerialReceive 		 = 32,
}; 
static int sPendingCallbacks;

namespace android {

static void status_callback(SerialStatus* status)
{
    pthread_mutex_lock(&sEventMutex);

    sPendingCallbacks |= kStatus;
    memcpy(&sSerialStatus, status, sizeof(sSerialStatus));

    pthread_cond_signal(&sEventCond);
    pthread_mutex_unlock(&sEventMutex);
}

static void receive_callback(SerialMsg* msg)
{
	LOGD("Inside %s", __FUNCTION__);
	pthread_mutex_lock(&sEventMutex);

	LOGD("Adding pending callback kSerialReceive");
    sPendingCallbacks |= kSerialReceive;
    LOGD("Copying msg, %d bytes to sSerialReceive", sizeof(sSerialReceive));
    memcpy(&sSerialReceive, msg, sizeof(sSerialReceive));
    LOGD("Msg copy complete");

    pthread_cond_signal(&sEventCond);
    pthread_mutex_unlock(&sEventMutex);
    LOGD("Leaving %s", __FUNCTION__);
}

SerialCallbacks sSerialCallbacks = {
    status_callback,
    receive_callback
};

static void android_hardware_SerialManager_class_init_native(JNIEnv* env, jclass clazz) {
    method_reportStatus = env->GetMethodID(clazz, "reportStatus", "(I)V");
    method_reportSerialMsg = env->GetMethodID(clazz, "reportSerialMsg", "()V");
}

static jboolean android_hardware_SerialManager_is_supported(JNIEnv* env, jclass clazz) {
    if (!sSerialInterface)
        sSerialInterface = serial_get_interface();
    return (sSerialInterface != NULL);
}

static jboolean android_hardware_SerialManager_init(JNIEnv* env, jobject obj)
{
    if (!sSerialInterface) {
    	LOGD("Attempting to get Serial interface");
        sSerialInterface = serial_get_interface();
    }

    if (!sSerialInterface) {
    	LOGE("No Serial interface found");
    	return false;
    } else {
    	LOGD("sSerialInterface=%p", sSerialInterface);
    }

    if (sSerialInterface->init(&sSerialCallbacks) != 0) {
    	LOGE("Error: Could not initialize Serial interface");
    	return false;
    }

    return true;
}

static void android_hardware_SerialManager_disable(JNIEnv* env, jobject obj)
{
    pthread_mutex_lock(&sEventMutex);
    sPendingCallbacks |= kDisableRequest;
    pthread_cond_signal(&sEventCond);
    pthread_mutex_unlock(&sEventMutex);
}

static void android_hardware_SerialManager_cleanup(JNIEnv* env, jobject obj)
{
    sSerialInterface->cleanup();
}

static jboolean android_hardware_SerialManager_start(JNIEnv* env, jobject obj, jstring device, jint baud)
{
    int result = -1;

    LOGD("native Starting");
    const char *c_device = env->GetStringUTFChars(device, NULL);
    result = sSerialInterface->start(c_device, baud);
    env->ReleaseStringUTFChars(device, c_device);

    return (result == 0);
}

static jboolean android_hardware_SerialManager_stop(JNIEnv* env, jobject obj)
{
    return (sSerialInterface->stop() == 0);
}

static void android_hardware_SerialManager_wait_for_event(JNIEnv* env, jobject obj)
{
    pthread_mutex_lock(&sEventMutex);
    while (sPendingCallbacks == 0) {
        pthread_cond_wait(&sEventCond, &sEventMutex);
    }

    // copy and clear the callback flags
    int pendingCallbacks = sPendingCallbacks;
    sPendingCallbacks = 0;
    
    // copy everything and unlock the mutex before calling into Java code to avoid the possibility
    // of timeouts in the SERIAL engine.
    if (pendingCallbacks & kStatus)
        memcpy(&sSerialStatusCopy, &sSerialStatus, sizeof(sSerialStatusCopy));
    if (pendingCallbacks & kSerialReceive)
        memcpy(&sSerialReceiveCopy, &sSerialReceive, sizeof(sSerialReceiveCopy));
    pthread_mutex_unlock(&sEventMutex);

    if (pendingCallbacks & kStatus) {
        env->CallVoidMethod(obj, method_reportStatus, sSerialStatusCopy.status);
    }
    if (pendingCallbacks & kSerialReceive) {
    	LOGD("Handling pending kSerialReceive callback inside %s, calling Java method method_reportSerialMsg",__FUNCTION__);
        env->CallVoidMethod(obj, method_reportSerialMsg);
    	LOGD("Finished kSerialReceive callback");
    }
    if (pendingCallbacks & kDisableRequest) {
        // don't need to do anything - we are just poking so wait_for_event will return.
    }
}

static jint android_hardware_SerialManager_read_serial_msg(JNIEnv* env, jobject obj, jbyteArray msgArray, jint buffer_size)
{
    // this should only be called from within a call to reportSerialMsg, so we don't need to lock here

    jbyte* msg = env->GetByteArrayElements(msgArray, 0);

    int length = strlen(sSerialReceiveCopy.data);
    if (length > buffer_size)
        length = buffer_size;
    memcpy(msg, sSerialReceiveCopy.data, length);

    env->ReleaseByteArrayElements(msgArray, msg, 0);
    return length;
}

static void android_hardware_SerialManager_serial_print(JNIEnv* env, jobject obj, jstring msg)
{
	LOGD("in android_hardware_SerialManager_serial_print");
    const char *c_msg = env->GetStringUTFChars(msg, NULL);
    sSerialInterface->print(c_msg);
    env->ReleaseStringUTFChars(msg, c_msg);
    LOGD("leaving android_hardware_SerialManager_serial_print");
}

static JNINativeMethod sMethods[] = {
     /* name, signature, funcPtr */
    {"class_init_native", "()V", (void *)android_hardware_SerialManager_class_init_native},
    {"native_is_supported", "()Z", (void*)android_hardware_SerialManager_is_supported},
    {"native_init", "()Z", (void*)android_hardware_SerialManager_init},
    {"native_disable", "()V", (void*)android_hardware_SerialManager_disable},
    {"native_cleanup", "()V", (void*)android_hardware_SerialManager_cleanup},
    {"native_start", "(Ljava/lang/String;I)Z", (void*)android_hardware_SerialManager_start},
    {"native_stop", "()Z", (void*)android_hardware_SerialManager_stop},
    {"native_wait_for_event", "()V", (void*)android_hardware_SerialManager_wait_for_event},
    {"native_read_serial_msg", "([BI)I", (void*)android_hardware_SerialManager_read_serial_msg},
    {"native_serial_print", "(Ljava/lang/String;)V", (void*)android_hardware_SerialManager_serial_print},
};

int register_android_hardware_SerialManager(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "android/serial/SerialManager", sMethods, NELEM(sMethods));
}

} /* namespace android */
