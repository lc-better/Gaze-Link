package com.demo.opencv.socialMedia;

import com.demo.opencv.BuildConfig;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

public class TwitterStartupTest {
    @Test
    public void missingTwitterKeysDoNotBlockStartup() {
        assumeTrue(BuildConfig.TWITTER_API_KEY.trim().isEmpty()
                || BuildConfig.TWITTER_API_SECRET.trim().isEmpty());

        TwitterClient client = new TwitterClient();
        client.init(null);

        assertNull(client.service);
    }
}
