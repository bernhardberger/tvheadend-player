package at.bernhardberger.tvhplayer.touch.guide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt

// Round 37's Guide drawings: Sunday, November 15, a little after 8:45 PM.

private class Channel(val id: String, val name: String, val tint: Color)

private val RidgeEarth = Channel("ridge-earth", "Ridge Earth", Color(0xFF3D481E))
private val AmberStage = Channel("amber-stage", "Amber Stage", Color(0xFF483D1E))
private val HarborSport = Channel("harbor-sport", "Harbor Sport", Color(0xFF1E3D48))
private val NorthlineNews = Channel("northline-news", "Northline News", Color(0xFF1E2848))
private val KiteKids = Channel("kite-kids", "Kite Kids", Color(0xFF471D27))
private val IonFrame = Channel("ion-frame", "Ion Frame", Color(0xFF471D3D))
private val HarborLights = Channel("harbor-lights", "Harbor Lights", Color(0xFF483D1E))
private val LanternHour = Channel("lantern-hour", "Lantern Hour", Color(0xFF47321D))
private val LittleOrbit = Channel("little-orbit", "Little Orbit", Color(0xFF1D4747))
private val GlassDrama = Channel("glass-drama", "Glass Drama", Color(0xFF2C3438))
private val NorthlineWorld = Channel("northline-world", "Northline World", Color(0xFF1E2848))

private val pictures = mutableMapOf<String, BitmapPainter>()

private fun picon(id: String) = pictures.getOrPut("picon:$id") { BitmapPainter(TouchFixtureArt.picon(id).asImageBitmap()) }

private fun art(name: String) = pictures.getOrPut("art:$name") { BitmapPainter(TouchFixtureArt.art(name).asImageBitmap()) }

private const val GLASS_HARBOR_SYNOPSIS = "Mara returns to the harbor office and finds the tide tables altered. " +
    "With a storm closing in, she has one night to learn who wants the Glass Harbor shut."
private const val COASTAL_MILES_SYNOPSIS = "Along the northern coast road, from the ferry towns to the lighthouse " +
    "at Grey Point. This week: crossing the tidal causeway before the water returns."
private const val RIDGE_LIGHT_SYNOPSIS = "Seasonal mountain light, forests and weather moving over a ridge. " +
    "This week the first frost reaches the upper pastures"

private fun channel(c: Channel, programs: List<GuideProgram>, gap: GuideGap? = null) =
    GuideChannel(c.id, c.name, picon(c.id), c.tint, programs, gap)

private fun program(
    c: Channel,
    start: String,
    title: String,
    line: String,
    still: String? = null,
    progress: Float? = null,
) = GuideProgram(
    id = "${c.id}-$start-$title", channelId = c.id, title = title, start = start, line = parts(line),
    still = still?.let(::art), progress = progress,
)

/** A drawn " • " line as its parts. */
private fun parts(line: String) = line.split(" • ")

/** Several start-time/title pairs sharing one line. */
private fun same(c: Channel, line: String, vararg rows: Pair<String, String>) =
    rows.map { (start, title) -> program(c, start, title, line) }

private fun base(channels: List<GuideChannel>) = GuideState(
    channels = channels,
    days = listOf("Today", "Tomorrow", "Tue 17", "Wed 18", "Thu 19", "Fri 20", "Sat 21", "Sun 22"),
    date = "Sunday, November 15",
    jumps = listOf("8:15 PM", "10:00 PM"),
    details = mapOf(GLASS_HARBOR_ID to glassHarborDetails()),
)

internal const val GLASS_HARBOR_ID = "amber-stage-7:50 PM-Glass Harbor"
internal const val COASTAL_MILES_ID = "amber-stage-10:05 PM-Coastal Miles"
internal const val RIDGE_LIGHT_ID = "ridge-earth-8:00 PM-Ridge Light"

private fun ridgeLightLead() = program(RidgeEarth, "8:00 PM", "Ridge Light", "Documentary • 13m left", "still-ridge-light", 0.78f)

private fun glassHarborLead() = program(AmberStage, "7:50 PM", "Glass Harbor", "Drama • 30m left", "still-glass-harbor", 0.66f)
    .copy(episode = "S2 E4 • The Long Tide", synopsis = GLASS_HARBOR_SYNOPSIS, timeLeft = "30m")

/** The tablet's magazine; the upright tablet's columns run further down the night. */
internal fun overviewFixture(upright: Boolean = false): GuideState {
    val ridge = listOf(ridgeLightLead()) + same(RidgeEarth, "S1 E6 • Documentary", "9:00 PM" to "Tide Watch") +
        program(RidgeEarth, "10:00 PM", "Snowline", "Documentary").copy(scheduled = true) +
        same(RidgeEarth, "Documentary", "10:45 PM" to "Six Bridges and a River That Changed Its Mind", "12:15 AM" to "Ridge at Night") +
        same(RidgeEarth, "S2 E2 • Documentary", "2:00 AM" to "Ridge Light") +
        same(RidgeEarth, "S1 E5 • Documentary", "3:00 AM" to "Tide Watch") +
        if (upright) {
            same(
                RidgeEarth, "Documentary", "4:00 AM" to "Snowline", "4:45 AM" to "Ridge at Night",
                "6:30 AM" to "First Light", "7:15 AM" to "Tide Watch", "8:15 AM" to "Ridge Light",
                "9:15 AM" to "High Pastures", "10:00 AM" to "Snowline", "10:45 AM" to "Tide Watch",
            )
        } else {
            emptyList()
        }
    val amber = listOf(glassHarborLead()) + same(AmberStage, "Talk show", "9:17 PM" to "The Kettle Hour") +
        program(AmberStage, "10:05 PM", "Coastal Miles", "Travel").copy(
            openLine = parts("Travel • 50m"), synopsis = COASTAL_MILES_SYNOPSIS,
        ) +
        same(AmberStage, "Talk show", "10:55 PM" to "Amber Late") +
        same(AmberStage, "S2 E4 • Drama", "11:40 PM" to "Glass Harbor") +
        same(AmberStage, "Talk show", "1:10 AM" to "The Kettle Hour") +
        same(AmberStage, "Travel", "2:00 AM" to "Coastal Miles") +
        if (upright) {
            same(AmberStage, "Talk show", "2:50 AM" to "Amber Late") +
                same(AmberStage, "S2 E3 • Drama", "3:35 AM" to "Glass Harbor") +
                same(AmberStage, "Talk show", "5:05 AM" to "The Kettle Hour") +
                same(AmberStage, "Magazine", "6:00 AM" to "Amber Morning") +
                same(AmberStage, "Travel", "8:30 AM" to "Coastal Miles") +
                same(AmberStage, "Talk show", "9:20 AM" to "The Kettle Hour") +
                same(AmberStage, "S2 E4 • Drama", "10:10 AM" to "Glass Harbor") +
                same(AmberStage, "Magazine", "11:40 AM" to "Amber Midday")
        } else {
            emptyList()
        }
    val harbor = listOf(
        program(HarborSport, "8:30 PM", "Harbor Kickoff", "Football • 1h 28m left", "still-harbor-kickoff", 0.16f)
            .copy(recording = true),
    ) + same(HarborSport, "Football", "10:15 PM" to "Extra Time") +
        same(HarborSport, "Basketball", "11:00 PM" to "River Court") +
        same(HarborSport, "Highlights", "12:45 AM" to "Harbor Kickoff") +
        same(HarborSport, "Football", "1:30 AM" to "Extra Time") +
        same(HarborSport, "Basketball", "2:15 AM" to "River Court") +
        same(HarborSport, "Football", "4:00 AM" to "Harbor Kickoff") +
        if (upright) {
            same(HarborSport, "Football", "5:45 AM" to "Extra Time") +
                same(HarborSport, "Athletics", "6:30 AM" to "Morning Run") +
                same(HarborSport, "Basketball", "8:00 AM" to "River Court") +
                same(HarborSport, "Highlights", "9:45 AM" to "Harbor Kickoff") +
                same(HarborSport, "Football", "10:30 AM" to "Extra Time") +
                same(HarborSport, "Basketball", "11:15 AM" to "Court Side") +
                same(HarborSport, "Football", "12:00 PM" to "Harbor Kickoff", "1:45 PM" to "Extra Time")
        } else {
            emptyList()
        }
    val northline = listOf(
        program(NorthlineNews, "8:00 PM", "Northline Tonight", "News • 13m left", "still-northline-studio", 0.78f),
    ) + same(NorthlineNews, "Weather", "9:00 PM" to "Skyglass Weather") +
        same(NorthlineNews, "Report", "9:15 PM" to "Field Desk") +
        same(NorthlineNews, "News", "10:00 PM" to "World Briefing", "10:30 PM" to "Northline Late") +
        same(NorthlineNews, "Weather", "11:00 PM" to "Skyglass Weather") +
        same(NorthlineNews, "Report", "11:15 PM" to "Field Desk") +
        if (upright) {
            same(NorthlineNews, "News", "12:00 AM" to "Northline Tonight", "1:00 AM" to "World Briefing") +
                same(NorthlineNews, "Report", "1:30 AM" to "Field Desk") +
                same(NorthlineNews, "Weather", "2:15 AM" to "Skyglass Weather") +
                same(NorthlineNews, "News", "2:30 AM" to "Northline Late", "3:00 AM" to "World Briefing") +
                same(NorthlineNews, "Report", "3:30 AM" to "Field Desk") +
                same(NorthlineNews, "Weather", "4:15 AM" to "Skyglass Weather")
        } else {
            emptyList()
        }
    val kite = listOf(
        program(KiteKids, "8:30 PM", "Paper Kite Tales", "Kids • 8m left", "still-paper-kite-tales", 0.68f),
    ) + same(KiteKids, "S1 E10 • Kids", "8:55 PM" to "Little Orbit") +
        same(KiteKids, "Kids", "9:20 PM" to "Paper Kite Tales", "9:45 PM" to "Good Night, Kites")
    return base(
        listOf(
            channel(RidgeEarth, ridge), channel(AmberStage, amber), channel(HarborSport, harbor),
            channel(NorthlineNews, northline), channel(KiteKids, kite, GuideGap("10:00 PM", "6:00 AM")),
        ),
    )
}

/** The phone's overview: what is on now, one row per channel. */
internal fun phoneOverviewFixture() = base(
    listOf(
        channel(RidgeEarth, listOf(ridgeLightLead())),
        channel(AmberStage, listOf(glassHarborLead())),
        channel(
            HarborSport,
            listOf(
                program(HarborSport, "8:30 PM", "Harbor Kickoff", "Football • 1h 28m left", "still-harbor-kickoff", 0.16f)
                    .copy(recording = true),
            ),
        ),
        channel(
            NorthlineNews,
            listOf(program(NorthlineNews, "8:00 PM", "Northline Tonight", "News • 13m left", "still-northline-studio", 0.78f)),
        ),
        channel(
            KiteKids, listOf(program(KiteKids, "8:30 PM", "Paper Kite Tales", "Kids • 8m left", "still-paper-kite-tales", 0.68f)),
        ),
        channel(IonFrame, listOf(program(IonFrame, "8:15 PM", "Ion Wake", "Sci-fi • 1h 18m left", "still-ion-wake", 0.29f))),
        channel(
            HarborLights,
            listOf(
                program(HarborLights, "8:00 PM", "Harbor Lights Live", "Music • 1h 13m left", "still-harbor-lights-live", 0.39f),
            ),
        ),
        channel(LanternHour, listOf(program(LanternHour, "8:00 PM", "Stories by Lamplight", "Culture • 43m left"))),
    ),
)

/** By channel: Ridge Earth's day with Ridge Light open; Glass Drama has no guide data. */
internal fun byChannelFixture(phone: Boolean = false, upright: Boolean = false): GuideState {
    val ridgeLight = program(RidgeEarth, "8:00 PM", "Ridge Light", "S2 E3 • 60m", "still-ridge-light", 0.78f).copy(
        art = art("ridge-light"),
        episode = "S2 E3 • Winter on the ridge",
        openLine = parts(if (phone) "8:00–9:00 PM • 60m" else "60m • Documentary • 2024"),
        timeRange = "8:00–9:00 PM",
        timeLeft = "13m",
        synopsis = if (phone) "$RIDGE_LIGHT_SYNOPSIS." else "$RIDGE_LIGHT_SYNOPSIS while the valley holds on to the last warm days.",
    )
    val withGenre = !phone
    val day = listOf(
        ridgeLight,
        program(RidgeEarth, "9:00 PM", "Tide Watch", if (withGenre) "S1 E6 • 60m • Documentary" else "S1 E6 • 60m", "still-tide-watch"),
        program(RidgeEarth, "10:00 PM", "Snowline", "45m • Documentary").copy(scheduled = true),
        program(RidgeEarth, "10:45 PM", "Six Bridges and a River That Changed Its Mind", "1h 30m • Documentary"),
        program(RidgeEarth, "12:15 AM", "Ridge at Night", "1h 45m • Documentary").copy(section = "Monday, November 16"),
        program(RidgeEarth, "2:00 AM", "Ridge Light", "S2 E2 • The long autumn • 60m", "ridge-light"),
        program(RidgeEarth, "3:00 AM", "Tide Watch", if (withGenre) "S1 E5 • 60m • Documentary" else "S1 E5 • 60m", "still-tide-watch"),
    ) + if (upright) {
        listOf(
            program(RidgeEarth, "4:00 AM", "Snowline", "45m • Documentary"),
            program(RidgeEarth, "4:45 AM", "Ridge at Night", "1h 45m • Documentary"),
            program(RidgeEarth, "6:30 AM", "First Light", "45m • Documentary"),
        )
    } else {
        emptyList()
    }
    val others = listOf(AmberStage, HarborSport, NorthlineNews, KiteKids, IonFrame, HarborLights, LanternHour, LittleOrbit)
        .map { channel(it, listOf(program(it, "8:00 PM", it.name, "60m"))) }
    return base(
        listOf(channel(RidgeEarth, day)) + others + channel(GlassDrama, emptyList()) +
            channel(NorthlineWorld, listOf(program(NorthlineWorld, "8:00 PM", "World Briefing", "30m • News"))),
    )
}

internal fun glassHarborDetails() = ProgramDetails(
    id = GLASS_HARBOR_ID,
    channelId = AmberStage.id,
    channel = AmberStage.name,
    channelLogo = picon(AmberStage.id),
    logoTint = AmberStage.tint,
    title = "Glass Harbor",
    episode = "S2 E4 • The Long Tide",
    metadata = parts("Drama • 2024 • 1h 27m"),
    ageRating = "12+",
    timeRange = "7:50–9:17 PM",
    progress = 0.66f,
    timeLeft = "30m",
    picture = art("still-glass-harbor"),
    art = art("glass-harbor"),
    synopsis = "$GLASS_HARBOR_SYNOPSIS Ines takes the night ferry to Northport with the only copy of the ledger, " +
        "and the pilot who knows the channel has not been seen since the morning tide.",
    otherAirings = listOf(
        ProgramAiring("gh-1", picon(AmberStage.id), AmberStage.tint, "Today 11:40 PM", parts("Amber Stage • S2 E4 • The Long Tide")),
        ProgramAiring(
            "gh-2", picon(AmberStage.id), AmberStage.tint, "Tomorrow 7:50 PM", parts("Amber Stage • S2 E5 • Low Water"),
            scheduled = true,
        ),
        ProgramAiring("gh-3", picon(GlassDrama.id), GlassDrama.tint, "Tue 1:15 PM", parts("Glass Drama • S2 E4 • The Long Tide")),
    ),
    credits = listOf(
        ProgramCredit(CreditRole.Cast, listOf("Rowan Frost", "Avery Lake", "Morgan Vale")),
        ProgramCredit(CreditRole.Director, listOf("Jamie Holt")),
        ProgramCredit(CreditRole.Writer, listOf("Blake Harbor", "Jamie Holt")),
    ),
    facts = listOf(
        ProgramFact(FactKey.Genre, "Drama"),
        ProgramFact(FactKey.Season, "2 of 3"),
        ProgramFact(FactKey.Episode, "4"),
        ProgramFact(FactKey.Year, "2024"),
        ProgramFact(FactKey.AgeRating, "12+"),
        ProgramFact(FactKey.FirstAired, "Oct 24, 2024"),
        ProgramFact(FactKey.Broadcast, "New"),
        ProgramFact(FactKey.Categories, "Drama • Mystery"),
    ),
)

/** Ridge Light's details, for the player's Info sheet; the Guide's own captures do not open them. */
internal fun ridgeLightDetails() = ProgramDetails(
    id = RIDGE_LIGHT_ID,
    channelId = RidgeEarth.id,
    channel = RidgeEarth.name,
    channelLogo = picon(RidgeEarth.id),
    logoTint = RidgeEarth.tint,
    title = "Ridge Light",
    episode = "S2 E3 • Winter on the ridge",
    metadata = parts("Documentary • 2024 • 60m"),
    ageRating = null,
    timeRange = "8:00–9:00 PM",
    progress = 0.78f,
    timeLeft = "13m",
    picture = art("still-ridge-light"),
    art = art("ridge-light"),
    synopsis = "$RIDGE_LIGHT_SYNOPSIS while the valley holds on to the last warm days.",
    otherAirings = listOf(
        ProgramAiring("rl-1", picon(RidgeEarth.id), RidgeEarth.tint, "Tomorrow 2:00 AM", parts("Ridge Earth • S2 E2 • The long autumn")),
        ProgramAiring("rl-2", picon(RidgeEarth.id), RidgeEarth.tint, "Sat 9:00 PM", parts("Ridge Earth • S2 E4 • First snow")),
        ProgramAiring(
            RIDGE_LIGHT_REPEAT_ID, picon(NorthlineWorld.id), NorthlineWorld.tint, "Sun 4:00 PM",
            parts("Northline World • S2 E3 • Winter on the ridge"),
        ),
    ),
    credits = listOf(ProgramCredit(CreditRole.Director, listOf("Morgan Vale"))),
    facts = listOf(
        ProgramFact(FactKey.Genre, "Documentary"),
        ProgramFact(FactKey.Season, "2"),
        ProgramFact(FactKey.Episode, "3"),
        ProgramFact(FactKey.Year, "2024"),
    ),
)

internal const val RIDGE_LIGHT_REPEAT_ID = "rl-northline"

/** The same episode repeated on another channel: its Details names that channel. */
internal fun ridgeLightRepeatDetails() = ridgeLightDetails().copy(
    id = RIDGE_LIGHT_REPEAT_ID,
    channelId = NorthlineWorld.id,
    channel = NorthlineWorld.name,
    channelLogo = picon(NorthlineWorld.id),
    logoTint = NorthlineWorld.tint,
    timeRange = "Sun 4:00–5:00 PM",
    progress = null,
    timeLeft = null,
    otherAirings = listOf(
        ProgramAiring(RIDGE_LIGHT_ID, picon(RidgeEarth.id), RidgeEarth.tint, "Today 8:00 PM", parts("Ridge Earth • S2 E3 • Winter on the ridge")),
    ),
)
