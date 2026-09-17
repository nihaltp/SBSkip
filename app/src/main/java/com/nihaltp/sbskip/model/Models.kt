package com.nihaltp.sbskip.model

enum class MediaType {
    VIDEO,
    AUDIO,
}

enum class DownloaderType {
    NEWPIPE,
}

enum class SponsorBlockCategory {
    SPONSOR, //              Sponsor segments
    SELF_PROMOTION, //       Unpaid/Self Promotion segments
    INTERACTION_REMINDER, // Interaction Reminder segments
    INTRO, //                Intermission/Intro Animation segments
    OUTRO, //                Endcards/Credits segments
    PREVIEW_RECAP, //        Preview/Recap segments
    HOOK, //                 Hook/Greetings segments
    FILLER_TANGENT, //       Tangents/Jokes segments
    MUSIC_OFFTOPIC, //       Music: Non-Music Section segments
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class SponsorBlockSettings(
    val enabled: Boolean = true,
    val categories: Set<SponsorBlockCategory> = SponsorBlockCategory.entries.toSet(),
)
