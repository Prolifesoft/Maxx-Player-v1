package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@UnstableApi
class ExampleUnitTest {
  @Test
  fun testExtractorsConfiguration() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    val httpFactory = DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true)
      .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
      .setConnectTimeoutMs(15000)
      .setReadTimeoutMs(15000)
    
    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    val extractorsFactory = DefaultExtractorsFactory()
      .setConstantBitrateSeekingEnabled(true)
      .setTsExtractorFlags(
        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
      )
      .setTsExtractorTimestampSearchBytes(1500 * 188)
    
    val extractors = extractorsFactory.createExtractors()
    assertTrue("Should include extractors", extractors.isNotEmpty())
    assertTrue("Should include TsExtractor", extractors.any { it is TsExtractor })
    
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
    assertNotNull(mediaSourceFactory)
  }
}



