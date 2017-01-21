/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.providers.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.providers.tv.TvProvider.DatabaseHelper;

/**
 * Convenient class for deleting transient rows. This ensures that the clean up job is done only
 * once after boot.
 */
public class TransientRowHelper {
    private static final String PREF_KEY_LAST_TRANSIENT_ROWS_DELETED_TIME =
            "pref_key_last_transient_rows_deleted_time";
    private static TransientRowHelper sInstance;

    private Context mContext;
    private DatabaseHelper mDatabaseHelper;
    @VisibleForTesting
    protected boolean mTransientRowsDeleted;

    /**
     * Returns the singleton TransientRowHelper instance.
     *
     * @param context The application context.
     */
    public static TransientRowHelper getInstance(Context context) {
        synchronized (TransientRowHelper.class) {
            if (sInstance == null) {
                sInstance = new TransientRowHelper(context);
            }
        }
        return sInstance;
    }

    @VisibleForTesting
    TransientRowHelper(Context context) {
        mContext = context;
        mDatabaseHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Ensures that transient rows, inserted previously before current boot, are deleted.
     */
    public synchronized void ensureOldTransientRowsDeleted() {
        if (mTransientRowsDeleted) {
            return;
        }
        mTransientRowsDeleted = true;
        if (getLastTransientRowsDeletedTime() > getBootCompletedTimeMillis()) {
            // This can be the second execution of TvProvider after boot since system kills
            // TvProvider in low memory conditions. If this is the case, we shouldn't delete
            // transient rows.
            return;
        }
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        // Delete all the transient programs and channels.
        db.delete(TvProvider.PROGRAMS_TABLE, Programs.COLUMN_TRANSIENT + "=1", null);
        db.delete(TvProvider.CHANNELS_TABLE, Channels.COLUMN_TRANSIENT + "=1", null);
        setLastTransientRowsDeletedTime();
    }

    @VisibleForTesting
    protected long getLastTransientRowsDeletedTime() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getLong(PREF_KEY_LAST_TRANSIENT_ROWS_DELETED_TIME, 0);
    }

    @VisibleForTesting
    protected void setLastTransientRowsDeletedTime() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit();
        editor.putLong(PREF_KEY_LAST_TRANSIENT_ROWS_DELETED_TIME, System.currentTimeMillis());
        editor.apply();
    }

    @VisibleForTesting
    protected long getBootCompletedTimeMillis() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }
}
