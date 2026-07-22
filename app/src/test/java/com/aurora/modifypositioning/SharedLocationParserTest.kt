package com.aurora.modifypositioning

import com.aurora.modifypositioning.util.SharedLocationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLocationParserTest {

    @Test
    fun parsesGeoPlainTextAndMapUrls() {
        val cases = listOf(
            "geo:31.2304,121.4737" to (31.2304 to 121.4737),
            "31.2304, 121.4737" to (31.2304 to 121.4737),
            "https://maps.example/place/@31.2304,121.4737,16z" to (31.2304 to 121.4737),
            "https://maps.example/?lat=31.2304&lng=121.4737" to (31.2304 to 121.4737),
            "https://maps.example/?lon=121.4737&lat=31.2304" to (31.2304 to 121.4737),
            "https://maps.example/?latitude=31.2304&longitude=121.4737" to (31.2304 to 121.4737),
        )

        cases.forEach { (value, expected) ->
            val target = SharedLocationParser.parse(value)
            assertEquals(expected.first, target?.latitude ?: Double.NaN, 0.0)
            assertEquals(expected.second, target?.longitude ?: Double.NaN, 0.0)
        }
    }

    @Test
    fun geoQueryUsesCoordinateAndLabel() {
        val target = SharedLocationParser.parse("geo:0,0?q=31.2304%2C121.4737%28People%27s%20Square%29")

        assertEquals(31.2304, target?.latitude ?: Double.NaN, 0.0)
        assertEquals(121.4737, target?.longitude ?: Double.NaN, 0.0)
        assertEquals("People's Square", target?.name)
    }

    @Test
    fun rejectsOutOfRangeAndUnexpandedShortLinks() {
        assertNull(SharedLocationParser.parse("91,121"))
        assertNull(SharedLocationParser.parse("geo:0,0?q=People%27s%20Square"))
        assertNull(SharedLocationParser.parse("https://maps.app.goo.gl/short-code"))
        assertTrue(SharedLocationParser.parse(" ") == null)
    }
}
