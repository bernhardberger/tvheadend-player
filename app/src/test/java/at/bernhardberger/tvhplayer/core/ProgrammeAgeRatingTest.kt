package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammeAgeRatingTest {
    @Test fun xmltvKeepsItsAuthority() {
        assertEquals("FSK 12", programmeAgeRating("XMLTV:FSK:12", 12))
        assertEquals("BBFC PG", programmeAgeRating(" XMLTV:BBFC:PG ", null))
    }

    @Test fun namedRatingsWinOverAge() {
        assertEquals("PG-13", programmeAgeRating("PG-13", 13))
        assertEquals("TV-14", programmeAgeRating("TV-14", null))
    }

    @Test fun xmltvWithoutAShortAuthorityShowsTheValue() {
        assertEquals("12+", programmeAgeRating("XMLTV:Freiwillige Selbstkontrolle Fernsehen:12", 12))
        assertEquals("12+", programmeAgeRating("XMLTV::12", 12))
        assertEquals("12+", programmeAgeRating("XMLTV:12", null))
        assertEquals("TV-14", programmeAgeRating("XMLTV:Some Long Board Name:TV-14", null))
    }

    @Test fun numericOrMissingLabelsUseAge() {
        assertEquals("12+", programmeAgeRating(null, 12))
        assertEquals("12+", programmeAgeRating("12", null))
        assertEquals("0+", programmeAgeRating(" ", 0))
        assertNull(programmeAgeRating(null, null))
        assertNull(programmeAgeRating(" ", null))
    }
}
