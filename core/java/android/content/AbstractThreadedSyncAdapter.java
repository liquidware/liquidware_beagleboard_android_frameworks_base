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

package android.content;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Process;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An abstract implementation of a SyncAdapter that spawns a thread to invoke a sync operation.
 * If a sync operation is already in progress when a startSync() request is received then an error
 * will be returned to the new request and the existing request will be allowed to continue.
 * When a startSync() is received and there is no sync operation in progress then a thread
 * will be started to run the operation and {@link #performSync} will be invoked on that thread.
 * If a cancelSync() is received that matches an existing sync operation then the thread
 * that is running that sync operation will be interrupted, which will indicate to the thread
 * that the sync has been canceled.
 *
 * @hide
 */
public abstract class AbstractThreadedSyncAdapter {
    private final Context mContext;
    private final AtomicInteger mNumSyncStarts;
    private final ISyncAdapterImpl mISyncAdapterImpl;

    // all accesses to this member variable must be synchronized on "this"
    private SyncThread mSyncThread;

    /** Kernel event log tag.  Also listed in data/etc/event-log-tags. */
    public static final int LOG_SYNC_DETAILS = 2743;

    /**
     * Creates an {@link AbstractThreadedSyncAdapter}.
     * @param context the {@link Context} that this is running within.
     */
    public AbstractThreadedSyncAdapter(Context context) {
        mContext = context;
        mISyncAdapterImpl = new ISyncAdapterImpl();
        mNumSyncStarts = new AtomicInteger(0);
        mSyncThread = null;
    }

    class ISyncAdapterImpl extends ISyncAdapter.Stub {
        public void startSync(ISyncContext syncContext, String authority, Account account,
                Bundle extras) {
            final SyncContext syncContextClient = new SyncContext(syncContext);

            boolean alreadyInProgress;
            // synchronize to make sure that mSyncThread doesn't change between when we
            // check it and when we use it
            synchronized (this) {
                if (mSyncThread == null) {
                    mSyncThread = new SyncThread(
                            "SyncAdapterThread-" + mNumSyncStarts.incrementAndGet(),
                            syncContextClient, authority, account, extras);
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    mSyncThread.start();
                    alreadyInProgress = false;
                } else {
                    alreadyInProgress = true;
                }
            }

            // do this outside since we don't want to call back into the syncContext while
            // holding the synchronization lock
            if (alreadyInProgress) {
                syncContextClient.onFinished(SyncResult.ALREADY_IN_PROGRESS);
            }
        }

        public void cancelSync(ISyncContext syncContext) {
            // synchronize to make sure that mSyncThread doesn't change between when we
            // check it and when we use it
            synchronized (this) {
                if (mSyncThread != null
                        && mSyncThread.mSyncContext.getISyncContext() == syncContext) {
                    mSyncThread.interrupt();
                }
            }
        }
    }

    /**
     * The thread that invokes performSync(). It also acquires the provider for this sync
     * before calling performSync and releases it afterwards. Cancel this thread in order to
     * cancel the sync.
     */
    private class SyncThread extends Thread {
        private final SyncContext mSyncContext;
        private final String mAuthority;
        private final Account mAccount;
        private final Bundle mExtras;

        private SyncThread(String name, SyncContext syncContext, String authority,
                Account account, Bundle extras) {
            super(name);
            mSyncContext = syncContext;
            mAuthority = authority;
            mAccount = account;
            mExtras = extras;
        }

        public void run() {
            if (isCanceled()) {
                return;
            }

            SyncResult syncResult = new SyncResult();
            ContentProviderClient provider = null;
            try {
                provider = mContext.getContentResolver().acquireContentProviderClient(mAuthority);
                if (provider != null) {
                    AbstractThreadedSyncAdapter.this.performSync(mAccount, mExtras,
                            mAuthority, provider, syncResult);
                } else {
                    // TODO(fredq) update the syncResults to indicate that we were unable to
                    // find the provider. maybe with a ProviderError?
                }
            } finally {
                if (provider != null) {
                    provider.release();
                }
                if (!isCanceled()) {
                    mSyncContext.onFinished(syncResult);
                }
                // synchronize so that the assignment will be seen by other threads
                // that also synchronize accesses to mSyncThread
                synchronized (this) {
                    mSyncThread = null;
                }
            }
        }

        private boolean isCanceled() {
            return Thread.currentThread().isInterrupted();
        }
    }

    /**
     * @return a reference to the ISyncAdapter interface into this SyncAdapter implementation.
     */
    public final ISyncAdapter getISyncAdapter() {
        return mISyncAdapterImpl;
    }

    /**
     * Perform a sync for this account. SyncAdapter-specific parameters may
     * be specified in extras, which is guaranteed to not be null. Invocations
     * of this method are guaranteed to be serialized.
     *
     * @param account the account that should be synced
     * @param extras SyncAdapter-specific parameters
     * @param authority the authority of this sync request
     * @param provider a ContentProviderClient that points to the ContentProvider for this
     *   authority
     * @param syncResult SyncAdapter-specific parameters
     */
    public abstract void performSync(Account account, Bundle extras,
            String authority, ContentProviderClient provider, SyncResult syncResult);
}