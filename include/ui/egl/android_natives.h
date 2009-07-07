/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef ANDROID_ANDROID_NATIVES_H
#define ANDROID_ANDROID_NATIVES_H

#include <sys/types.h>
#include <string.h>

#include <hardware/gralloc.h>

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************************/

#define ANDROID_NATIVE_MAKE_CONSTANT(a,b,c,d) \
    (((unsigned)(a)<<24)|((unsigned)(b)<<16)|((unsigned)(c)<<8)|(unsigned)(d))

#define ANDROID_NATIVE_WINDOW_MAGIC \
    ANDROID_NATIVE_MAKE_CONSTANT('_','w','n','d')

#define ANDROID_NATIVE_BUFFER_MAGIC \
    ANDROID_NATIVE_MAKE_CONSTANT('_','b','f','r')

// ---------------------------------------------------------------------------

struct android_native_buffer_t;

// ---------------------------------------------------------------------------

struct android_native_base_t
{
    /* a magic value defined by the actual EGL native type */
    int magic;
    
    /* the sizeof() of the actual EGL native type */
    int version;

    void* reserved[4];

    /* reference-counting interface */
    void (*incRef)(struct android_native_base_t* base);
    void (*decRef)(struct android_native_base_t* base);
};

// ---------------------------------------------------------------------------

struct android_native_window_t 
{
#ifdef __cplusplus
    android_native_window_t()
        : flags(0), minSwapInterval(0), maxSwapInterval(0), xdpi(0), ydpi(0)
    {
        common.magic = ANDROID_NATIVE_WINDOW_MAGIC;
        common.version = sizeof(android_native_window_t);
        memset(common.reserved, 0, sizeof(common.reserved));
    }
#endif
    
    struct android_native_base_t common;

    /* flags describing some attributes of this surface or its updater */
    const uint32_t flags;
    
    /* min swap interval supported by this updated */
    const int   minSwapInterval;

    /* max swap interval supported by this updated */
    const int   maxSwapInterval;

    /* horizontal and vertical resolution in DPI */
    const float xdpi;
    const float ydpi;

    /* Some storage reserved for the OEM's driver. */
    intptr_t    oem[4];
        

    /*
     * Set the swap interval for this surface.
     * 
     * Returns 0 on success or -errno on error.
     */
    int     (*setSwapInterval)(struct android_native_window_t* window,
                int interval);
    
    /*
     * hook called by EGL to acquire a buffer. After this call, the buffer
     * is not locked, so its content cannot be modified.
     * this call may block if no buffers are available.
     * 
     * Returns 0 on success or -errno on error.
     */
    int     (*dequeueBuffer)(struct android_native_window_t* window, 
                struct android_native_buffer_t** buffer);

    /*
     * hook called by EGL to lock a buffer. This MUST be called before modifying
     * the content of a buffer. The buffer must have been acquired with 
     * dequeueBuffer first.
     * 
     * Returns 0 on success or -errno on error.
     */
    int     (*lockBuffer)(struct android_native_window_t* window,
                struct android_native_buffer_t* buffer);
   /*
    * hook called by EGL when modifications to the render buffer are done. 
    * This unlocks and post the buffer.
    * 
    * Buffers MUST be queued in the same order than they were dequeued.
    * 
    * Returns 0 on success or -errno on error.
    */
    int     (*queueBuffer)(struct android_native_window_t* window,
                struct android_native_buffer_t* buffer);

    
    void* reserved_proc[5];
};

// ---------------------------------------------------------------------------

/* FIXME: this is legacy for pixmaps */
struct egl_native_pixmap_t
{
    int32_t     version;    /* must be 32 */
    int32_t     width;
    int32_t     height;
    int32_t     stride;
    uint8_t*    data;
    uint8_t     format;
    uint8_t     rfu[3];
    union {
        uint32_t    compressedFormat;
        int32_t     vstride;
    };
    int32_t     reserved;
};

/*****************************************************************************/

#ifdef __cplusplus
}
#endif


/*****************************************************************************/

#ifdef __cplusplus

#include <utils/RefBase.h>

namespace android {

/*
 * This helper class turns an EGL android_native_xxx type into a C++
 * reference-counted object; with proper type conversions.
 */
template <typename NATIVE_TYPE, typename TYPE, typename REF>
class EGLNativeBase : public NATIVE_TYPE, public REF
{
protected:
    typedef EGLNativeBase<NATIVE_TYPE, TYPE, REF> BASE;
    EGLNativeBase() : NATIVE_TYPE(), REF() {
        NATIVE_TYPE::common.incRef = incRef;
        NATIVE_TYPE::common.decRef = decRef;
    }
    static inline TYPE* getSelf(NATIVE_TYPE* self) {
        return static_cast<TYPE*>(self);
    }
    static inline TYPE const* getSelf(NATIVE_TYPE const* self) {
        return static_cast<TYPE const *>(self);
    }
    static inline TYPE* getSelf(android_native_base_t* base) {
        return getSelf(reinterpret_cast<NATIVE_TYPE*>(base));
    }
    static inline TYPE const * getSelf(android_native_base_t const* base) {
        return getSelf(reinterpret_cast<NATIVE_TYPE const*>(base));
    }
    static void incRef(android_native_base_t* base) {
        EGLNativeBase* self = getSelf(base);
        self->incStrong(self);
    }
    static void decRef(android_native_base_t* base) {
        EGLNativeBase* self = getSelf(base);
        self->decStrong(self);
    }
};

} // namespace android
#endif // __cplusplus

/*****************************************************************************/

#endif /* ANDROID_ANDROID_NATIVES_H */