package com.example.data.model

import com.example.network.GeminiClipOutput
import com.example.network.GeminiWordCaption

data class SampleVideo(
    val title: String,
    val description: String,
    val url: String, // Or mock path
    val durationSeconds: Long,
    val transcript: String,
    val mockClips: List<GeminiClipOutput>
)

object SampleVideos {
    val list = listOf(
        SampleVideo(
            title = " Lex Fridman & Joe Rogan: The Future of AI Coding",
            description = "A deep conversation on how AI coding agents are changing software engineering, productivity, and the future of tech startups.",
            url = "sample_video_ai",
            durationSeconds = 120,
            transcript = "AI is fundamentally shifting what it means to code. It is not about writing syntax anymore. You can just prompt your agent to build a full working project in seconds. The craft moves to architecture, systems design, and deeply understanding user intent. If you can describe it, the AI can build it. The feedback loops are getting so fast that you can iterate and deploy a production-ready application within a single afternoon. It unlocks creativity in a way we have never seen before.",
            mockClips = listOf(
                GeminiClipOutput(
                    title = "🚀 The Death of Syntax Writing",
                    startSec = 0,
                    endSec = 35,
                    viralScore = 98,
                    viralReason = "Strong immediate hook on 'AI shifting what it means to code' which is a highly polarizing and trending subject.",
                    captions = listOf(
                        GeminiWordCaption("AI", 200, 500), GeminiWordCaption("is", 500, 700),
                        GeminiWordCaption("fundamentally", 700, 1100), GeminiWordCaption("shifting", 1100, 1400),
                        GeminiWordCaption("what", 1400, 1600), GeminiWordCaption("it", 1600, 1800),
                        GeminiWordCaption("means", 1800, 2100), GeminiWordCaption("to", 2100, 2300),
                        GeminiWordCaption("code.", 2300, 2800), GeminiWordCaption("It", 3200, 3400),
                        GeminiWordCaption("is", 3400, 3600), GeminiWordCaption("not", 3600, 3800),
                        GeminiWordCaption("about", 3800, 4100), GeminiWordCaption("writing", 4100, 4400),
                        GeminiWordCaption("syntax", 4400, 4800), GeminiWordCaption("anymore.", 4800, 5400),
                        GeminiWordCaption("You", 5800, 6000), GeminiWordCaption("can", 6000, 6200),
                        GeminiWordCaption("just", 6200, 6400), GeminiWordCaption("prompt", 6400, 6800),
                        GeminiWordCaption("your", 6800, 7000), GeminiWordCaption("agent", 7000, 7300),
                        GeminiWordCaption("to", 7300, 7500), GeminiWordCaption("build", 7500, 7800),
                        GeminiWordCaption("a", 7800, 7900), GeminiWordCaption("full", 7900, 8100),
                        GeminiWordCaption("working", 8100, 8400), GeminiWordCaption("project", 8400, 8800),
                        GeminiWordCaption("in", 8800, 8900), GeminiWordCaption("seconds.", 8900, 9500),
                        GeminiWordCaption("The", 10000, 10200), GeminiWordCaption("craft", 10200, 10600),
                        GeminiWordCaption("moves", 10600, 10900), GeminiWordCaption("to", 10900, 11100),
                        GeminiWordCaption("architecture,", 11100, 11800), GeminiWordCaption("systems", 12000, 12400),
                        GeminiWordCaption("design,", 12400, 13000), GeminiWordCaption("and", 13200, 13400),
                        GeminiWordCaption("deeply", 13400, 13800), GeminiWordCaption("understanding", 13800, 14500),
                        GeminiWordCaption("user", 14500, 14800), GeminiWordCaption("intent.", 14800, 15500)
                    )
                ),
                GeminiClipOutput(
                    title = "⚡ Creating Apps in One Afternoon",
                    startSec = 36,
                    endSec = 75,
                    viralScore = 94,
                    viralReason = "Practical, inspiring business hook illustrating how quick and cheap shipping software is nowadays.",
                    captions = listOf(
                        GeminiWordCaption("If", 36000, 36200), GeminiWordCaption("you", 36200, 36400),
                        GeminiWordCaption("can", 36400, 36600), GeminiWordCaption("describe", 36600, 3700),
                        GeminiWordCaption("it,", 37000, 37400), GeminiWordCaption("the", 37600, 37800),
                        GeminiWordCaption("AI", 37800, 38100), GeminiWordCaption("can", 38100, 38300),
                        GeminiWordCaption("build", 38300, 38600), GeminiWordCaption("it.", 38600, 39000),
                        GeminiWordCaption("The", 39500, 39700), GeminiWordCaption("feedback", 39700, 40100),
                        GeminiWordCaption("loops", 40100, 40400), GeminiWordCaption("are", 40400, 40600),
                        GeminiWordCaption("getting", 40600, 40900), GeminiWordCaption("so", 40900, 41100),
                        GeminiWordCaption("fast", 41100, 41600), GeminiWordCaption("that", 41800, 42000),
                        GeminiWordCaption("you", 42000, 42200), GeminiWordCaption("can", 42200, 42400),
                        GeminiWordCaption("iterate", 42400, 42800), GeminiWordCaption("and", 42800, 43000),
                        GeminiWordCaption("deploy", 43000, 43400), GeminiWordCaption("a", 43400, 43500),
                        GeminiWordCaption("production-ready", 43500, 44200), GeminiWordCaption("application", 44200, 44800),
                        GeminiWordCaption("within", 44800, 45100), GeminiWordCaption("a", 45100, 45200),
                        GeminiWordCaption("single", 45200, 45500), GeminiWordCaption("afternoon.", 45500, 46100),
                        GeminiWordCaption("It", 47000, 47200), GeminiWordCaption("unlocks", 47200, 47700),
                        GeminiWordCaption("creativity", 47700, 48300), GeminiWordCaption("in", 48300, 48500),
                        GeminiWordCaption("a", 48500, 48600), GeminiWordCaption("way", 48600, 48900),
                        GeminiWordCaption("we", 48900, 49100), GeminiWordCaption("have", 49100, 49300),
                        GeminiWordCaption("never", 49300, 49600), GeminiWordCaption("seen", 49600, 49900),
                        GeminiWordCaption("before.", 49900, 50500)
                    )
                )
            )
        ),
        SampleVideo(
            title = "📈 Unlocking Your 100% Potential",
            description = "A powerful, high-energy motivational speech on building daily routines, beating procrastination, and mental focus.",
            url = "sample_video_routine",
            durationSeconds = 90,
            transcript = "Procrastination is not a time management problem. It is an emotional regulation problem. When you avoid a task, you are avoiding the bad feeling that task gives you. Work through that first 5 minutes of discomfort. That is where progress happens. Consistency over intensity always wins.",
            mockClips = listOf(
                GeminiClipOutput(
                    title = "🧠 Unmasking Procrastination",
                    startSec = 0,
                    endSec = 40,
                    viralScore = 96,
                    viralReason = "Strong biological and emotional realization. Highly relatable content with massive saving rates.",
                    captions = listOf(
                        GeminiWordCaption("Procrastination", 200, 900), GeminiWordCaption("is", 900, 1100),
                        GeminiWordCaption("not", 1100, 1400), GeminiWordCaption("a", 1400, 1500),
                        GeminiWordCaption("time", 1500, 1800), GeminiWordCaption("management", 1800, 2300),
                        GeminiWordCaption("problem.", 2300, 2900), GeminiWordCaption("It", 3400, 3600),
                        GeminiWordCaption("is", 3600, 3800), GeminiWordCaption("an", 3800, 4000),
                        GeminiWordCaption("emotional", 4000, 4500), GeminiWordCaption("regulation", 4500, 5000),
                        GeminiWordCaption("problem.", 5000, 5700), GeminiWordCaption("When", 6200, 6500),
                        GeminiWordCaption("you", 6500, 6700), GeminiWordCaption("avoid", 6700, 7100),
                        GeminiWordCaption("a", 7100, 7200), GeminiWordCaption("task,", 7200, 7600),
                        GeminiWordCaption("you", 8000, 8200), GeminiWordCaption("are", 8200, 8400),
                        GeminiWordCaption("avoiding", 8400, 8900), GeminiWordCaption("the", 8900, 9100),
                        GeminiWordCaption("bad", 9100, 9300), GeminiWordCaption("feeling", 9300, 9700),
                        GeminiWordCaption("that", 9700, 9900), GeminiWordCaption("task", 9900, 10200),
                        GeminiWordCaption("gives", 10200, 10500), GeminiWordCaption("you.", 10500, 1100)
                    )
                ),
                GeminiClipOutput(
                    title = "🔥 The First 5 Minutes Rule",
                    startSec = 41,
                    endSec = 80,
                    viralScore = 91,
                    viralReason = "Actionable self-help hack. Easy to remember and execute, creating great commentary engagement.",
                    captions = listOf(
                        GeminiWordCaption("Work", 41000, 41300), GeminiWordCaption("through", 41300, 41600),
                        GeminiWordCaption("that", 41600, 41800), GeminiWordCaption("first", 41800, 42100),
                        GeminiWordCaption("5", 42100, 42300), GeminiWordCaption("minutes", 42300, 42700),
                        GeminiWordCaption("of", 42700, 42900), GeminiWordCaption("discomfort.", 42900, 43600),
                        GeminiWordCaption("That", 44200, 44500), GeminiWordCaption("is", 44500, 44700),
                        GeminiWordCaption("where", 44700, 45000), GeminiWordCaption("progress", 45000, 45500),
                        GeminiWordCaption("happens.", 45500, 46100), GeminiWordCaption("Consistency", 46800, 47500),
                        GeminiWordCaption("over", 47500, 47800), GeminiWordCaption("intensity", 47800, 48400),
                        GeminiWordCaption("always", 48400, 48800), GeminiWordCaption("wins.", 48800, 49400)
                    )
                )
            )
        )
    )
}
