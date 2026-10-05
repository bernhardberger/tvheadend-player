package at.bernhardberger.tvhplayer.ui.player

/** Player behaviours that ship switched off; the code behind each stays in place. */
internal object PlayerFeatures {
    /**
     * Left/Right on the live channel card change channel, and holding either opens the channel rail
     * stepping that way. Off, the card takes neither key and shows no arrows.
     */
    const val channelCardStepping = false
}
