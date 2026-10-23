package com.dailynews.reader.data

import kotlinx.serialization.encodeToString

object DemoContent {
    val paragraphs = listOf(
        "A central bank's decision to hold interest rates steady can be as significant as a decision to raise them. Policymakers must weigh the need to bring inflation under control against the risk of slowing economic activity too sharply.",
        "Higher borrowing costs tend to reduce spending by households and businesses. However, their effects take time to spread through the economy, which is why officials often wait for further evidence before changing policy again.",
        "Although a decline in energy prices may ease pressure on consumers, it does not necessarily mean that underlying inflation has disappeared. Services prices and wage growth can remain persistent even when headline figures improve.",
        "For readers of economic news, the distinction between a forecast and a commitment is essential. When a central bank says it could raise rates, it is describing a possibility rather than announcing a decision. Paying attention to these small words helps readers understand what a report actually claims."
    )
    val analysis = ArticleAnalysis(
        summaryEn = "Central banks balance inflation against economic growth. Interest-rate changes take time to work, and falling energy prices do not guarantee that inflation is under control. Readers should distinguish forecasts from actual policy commitments.",
        summaryZh = "这篇原创教学样文解释央行如何权衡通胀和增长、利率政策的滞后效应，以及新闻中预测与正式决策的区别。内容不指向任何真实新闻事件。",
        translations = listOf(
            "央行决定维持利率不变，可能与决定加息同样重要。政策制定者必须权衡控制通胀的需要，以及经济活动放缓过快的风险。",
            "借贷成本上升通常会减少家庭和企业的支出。不过，其影响需要时间才能传导到整个经济，这也是官员往往会等待更多证据再调整政策的原因。",
            "尽管能源价格下降可能缓解消费者的压力，但这不一定意味着潜在通胀已经消失。即使总体数据改善，服务价格和工资增长仍可能持续。",
            "对于经济新闻读者，区分预测与承诺至关重要。央行说它可能加息，是在描述一种可能性，而不是宣布一项决定。留意这些小词有助于读者理解报道实际声称了什么。"
        ).mapIndexed { i, text -> ParagraphTranslation("p${i + 1}", text) },
        vocabulary = listOf(
            WordInsight("p1", "hold interest rates steady", "维持利率不变", "hold steady", "动词短语", explanation = "hold … steady 表示使某事物保持稳定。"),
            WordInsight("p1", "weigh", "权衡；仔细考虑", "weigh", "动词", "/weɪ/", "此处不是称重，而是在两种目标或风险之间进行衡量。"),
            WordInsight("p1", "inflation", "通货膨胀", "inflation", "名词", "/ɪnˈfleɪʃən/"),
            WordInsight("p2", "borrowing costs", "借贷成本", "borrowing cost", "名词短语"),
            WordInsight("p2", "evidence", "证据；依据", "evidence", "不可数名词", "/ˈevɪdəns/"),
            WordInsight("p3", "ease pressure", "缓解压力", "ease pressure", "动词短语"),
            WordInsight("p3", "underlying inflation", "潜在通胀；较持久的通胀趋势", "underlying inflation", "名词短语"),
            WordInsight("p3", "persistent", "持续存在的；难以消退的", "persistent", "形容词", "/pərˈsɪstənt/"),
            WordInsight("p4", "commitment", "承诺；明确的行动意向", "commitment", "名词", "/kəˈmɪtmənt/")
        ),
        sentences = listOf(SentenceInsight("p3", paragraphs[2].substringBefore(". ") + ".",
            "尽管能源价格下降可能缓解消费者的压力，但这不一定意味着潜在通胀已经消失。",
            "Although 引导让步从句；主句主干是 it does not necessarily mean；that 引导宾语从句。may 表示可能，not necessarily 表示不一定，两者都不能省略。"))
    )
    val article = Article(
        id = "welcome-demo", sourceId = "demo", sourceName = "原创教学样文", url = "https://example.org/dailynews-demo",
        title = "Reading between the lines of economic news",
        description = "先用一篇原创样文体验点词、段落翻译和句子分析。此内容不是实时新闻。",
        category = Category.ECONOMY.name, contentType = "教学样文", isDemo = true,
        paragraphsJson = encodeParagraphs(paragraphs), contentHash = digest(paragraphs.joinToString("\n")),
        analysisJson = newsJson.encodeToString(analysis), processing = "ready",
    )

    val sources = SourceCatalog.sources
}
