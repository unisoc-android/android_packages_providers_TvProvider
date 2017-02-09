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
 * limitations under the License.
 */

package com.android.providers.tv;

import com.google.android.collect.Sets;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvContract.WatchedPrograms;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TransientRowHelperTests extends AndroidTestCase {
    private static final String FAKE_INPUT_ID = "TransientRowHelperTests";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;
    private RebootSimulatingTransientRowHelper mTransientRowHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = new MockContentResolver();
        mResolver.addProvider(Settings.AUTHORITY, new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return new Bundle();
            }
        });

        mProvider = new TvProviderForTesting();
        mResolver.addProvider(TvContract.AUTHORITY, mProvider);

        setContext(new MockTvProviderContext(mResolver, getContext()));

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);
        mTransientRowHelper = new RebootSimulatingTransientRowHelper(getContext());
        mProvider.setTransientRowHelper(mTransientRowHelper);
        Utils.clearTvProvider(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    private static class Program {
        long id;
        final boolean isTransient;

        Program(boolean isTransient) {
            this(-1, isTransient);
        }

        Program(long id, boolean isTransient) {
            this.id = id;
            this.isTransient = isTransient;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Program)) {
                return false;
            }
            Program that = (Program) obj;
            return Objects.equals(id, that.id)
                    && Objects.equals(isTransient, that.isTransient);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, isTransient);
        }

        @Override
        public String toString() {
            return "Program(id=" + id + ",isTransient=" + isTransient + ")";
        }
    }

    private long insertChannel(boolean isTransient) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        values.put(Channels.COLUMN_TRANSIENT, isTransient ? 1 : 0);
        Uri uri = mResolver.insert(Channels.CONTENT_URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    private void insertPrograms(long channelId, Program... programs) {
        insertPrograms(channelId, Arrays.asList(programs));
    }

    private void insertPrograms(long channelId, Collection<Program> programs) {
        ContentValues values = new ContentValues();
        values.put(Programs.COLUMN_CHANNEL_ID, channelId);
        for (Program program : programs) {
            values.put(Programs.COLUMN_TRANSIENT, program.isTransient ? 1 : 0);
            Uri uri = mResolver.insert(Programs.CONTENT_URI, values);
            assertNotNull(uri);
            program.id = ContentUris.parseId(uri);
        }
    }

    private Set<Program> queryPrograms() {
        String[] projection = new String[] {
            Programs._ID,
            Programs.COLUMN_TRANSIENT,
        };

        Cursor cursor = mResolver.query(Programs.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            Set<Program> programs = Sets.newHashSet();
            while (cursor.moveToNext()) {
                programs.add(new Program(cursor.getLong(0), cursor.getInt(1) == 1));
            }
            return programs;
        } finally {
            cursor.close();
        }
    }

    private long getChannelCount() {
        String[] projection = new String[] {
            Channels._ID,
        };

        Cursor cursor = mResolver.query(Channels.CONTENT_URI, projection, null, null, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    public void testTransientRowsAreDeletedAfterReboot() {
        Program transientProgramInTransientChannel = new Program(true /* transient */);
        Program permanentProgramInTransientChannel = new Program(false /* transient */);
        Program transientProgramInPermanentChannel = new Program(true /* transient */);
        Program permanentProgramInPermanentChannel = new Program(false /* transient */);
        long transientChannelId = insertChannel(true /* transient */);
        long permanentChannelId = insertChannel(false /* transient */);
        insertPrograms(transientChannelId, transientProgramInTransientChannel);
        insertPrograms(transientChannelId, permanentProgramInTransientChannel);
        insertPrograms(permanentChannelId, transientProgramInPermanentChannel);
        insertPrograms(permanentChannelId, permanentProgramInPermanentChannel);

        assertEquals("Before reboot all the programs inserted should exist.",
                Sets.newHashSet(transientProgramInTransientChannel, permanentProgramInTransientChannel,
                        transientProgramInPermanentChannel, permanentProgramInPermanentChannel),
                queryPrograms());
        assertEquals("Before reboot the channels inserted should exist.",
                2, getChannelCount());

        mTransientRowHelper.simulateReboot();
        assertEquals("Transient program and programs in transient channel should be removed.",
                Sets.newHashSet(permanentProgramInPermanentChannel), queryPrograms());
        assertEquals("Transient channel should not be removed.",
                1, getChannelCount());
    }

    private class RebootSimulatingTransientRowHelper extends TransientRowHelper {
        private long mLastTransientRowsRemoveTime;
        private Long mBootTime;

        private RebootSimulatingTransientRowHelper(Context context) {
            super(context);
        }

        @Override
        protected long getLastTransientRowsDeletedTime() {
            return mLastTransientRowsRemoveTime;
        }

        @Override
        protected void setLastTransientRowsDeletedTime() {
            mLastTransientRowsRemoveTime = System.currentTimeMillis();
        }

        @Override
        protected long getBootCompletedTimeMillis() {
            if (mBootTime != null) {
                return mBootTime;
            }
            return System.currentTimeMillis() - SystemClock.elapsedRealtime();
        }

        private void simulateReboot() {
            mTransientRowsDeleted = false;
            mBootTime = System.currentTimeMillis();
        }
    }
}
