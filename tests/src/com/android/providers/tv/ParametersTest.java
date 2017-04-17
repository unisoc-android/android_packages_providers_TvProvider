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

import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

public class ParametersTest extends AndroidTestCase {
    private static final String FAKE_INPUT_ID = "ParametersTest";

    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;

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
        Utils.clearTvProvider(mResolver);
    }

    @Override
    protected void tearDown() throws Exception {
        Utils.clearTvProvider(mResolver);
        mProvider.shutdown();
        super.tearDown();
    }

    private ContentValues createDummyChannelValues(boolean preview) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, FAKE_INPUT_ID);
        values.put(Channels.COLUMN_INTERNAL_PROVIDER_ID, "ID-4321");
        values.put(Channels.COLUMN_TYPE, preview ? Channels.TYPE_PREVIEW : Channels.TYPE_OTHER);
        values.put(Channels.COLUMN_SERVICE_TYPE, Channels.SERVICE_TYPE_AUDIO_VIDEO);
        values.put(Channels.COLUMN_DISPLAY_NUMBER, "1");
        values.put(Channels.COLUMN_VIDEO_FORMAT, Channels.VIDEO_FORMAT_480P);

        return values;
    }

    private void verifyChannelCountWithPreview(int expectedCount, boolean preview) {
        Uri channelUri = Channels.CONTENT_URI.buildUpon()
                .appendQueryParameter(TvContract.PARAM_PREVIEW, String.valueOf(preview)).build();
        try (Cursor cursor = mResolver.query(
                channelUri, new String[] {Channels.COLUMN_TYPE}, null, null, null)) {
            assertNotNull(cursor);
            assertEquals("Query:{Uri=" + channelUri + "}", expectedCount, cursor.getCount());
        }
    }

    public void testTypePreviewQueryChannel() {
        // Check if there is not any preview and non-preview channels.
        verifyChannelCountWithPreview(0, true);
        verifyChannelCountWithPreview(0, false);
        // Insert one preview channel and then check if the count of preview channels is 0 and the
        // count of non-preview channels is 0.
        ContentValues previewChannelContentValue = createDummyChannelValues(true);
        mResolver.insert(Channels.CONTENT_URI, previewChannelContentValue);
        verifyChannelCountWithPreview(1, true);
        verifyChannelCountWithPreview(0, false);
        // Insert one non-preview channel and then check if the count of preview channels or
        // non-preview channels are both 1.
        ContentValues nonPreviewChannelContentValue = createDummyChannelValues(false);
        mResolver.insert(Channels.CONTENT_URI, nonPreviewChannelContentValue);
        verifyChannelCountWithPreview(1, true);
        verifyChannelCountWithPreview(1, false);
    }
}
