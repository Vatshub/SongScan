package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.analyzer.MusicTheory
import com.example.data.model.SectionType
import com.example.data.model.SongSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TrackScan", appName)
  }

  @Test
  fun `verify music theory camelot mapping`() {
    val fSharpMinor = MusicTheory.getCamelotInfo("F# Minor")
    assertEquals("11A", fSharpMinor.code)
    assertEquals("A Major (11B)", fSharpMinor.relativeKey)

    val cMajor = MusicTheory.getCamelotInfo("C Major")
    assertEquals("8B", cMajor.code)
    assertEquals("A Minor (8A)", cMajor.relativeKey)
  }

  @Test
  fun `verify song section formatting`() {
    val section = SongSection(
      name = "Chorus 1",
      type = SectionType.CHORUS,
      startMs = 64000L,
      endMs = 96000L,
      energy = 0.92f,
      description = "Main Hook"
    )
    assertEquals("1:04 - 1:36", section.formatRange())
    assertEquals(32000L, section.durationMs)
  }
}
