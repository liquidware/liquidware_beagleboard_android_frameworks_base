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

package android.renderscript;

import android.util.Log;

/**
 * @hide
 *
 **/
class BaseObj {

    BaseObj(RenderScript rs) {
        mRS = rs;
        mID = 0;
    }

    public int getID() {
        return mID;
    }

    int mID;
    String mName;
    RenderScript mRS;

    public void setName(String s) throws IllegalStateException, IllegalArgumentException
    {
        if(s.length() < 1) {
            throw new IllegalArgumentException("setName does not accept a zero length string.");
        }
        if(mName != null) {
            throw new IllegalArgumentException("setName object already has a name.");
        }

        try {
            byte[] bytes = s.getBytes("UTF-8");
            mRS.nAssignName(mID, bytes);
            mName = s;
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected void finalize() throws Throwable
    {
        if (mID != 0) {
            Log.v(RenderScript.LOG_TAG,
                  "Element finalized without having released the RS reference.");
        }
        super.finalize();
    }
}
