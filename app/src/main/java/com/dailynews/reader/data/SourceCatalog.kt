package com.dailynews.reader.data

/** Public publisher feeds verified in docs/feed-verification.json. Seed once per catalog version. */
object SourceCatalog {
    const val VERSION = 2
    private fun source(id: String, name: String, url: String, category: Category = Category.GENERAL,
        region: Region = Region.GLOBAL) = NewsSource(id, name, url, category.name, region.name)
    val sources = listOf(
        source("bbc-world", "BBC · World", "https://feeds.bbci.co.uk/news/world/rss.xml"),
        source("bbc-business", "BBC · Business", "https://feeds.bbci.co.uk/news/business/rss.xml", Category.ECONOMY),
        source("guardian-world", "Guardian · World", "https://www.theguardian.com/world/rss"),
        source("guardian-culture", "Guardian · Culture", "https://www.theguardian.com/culture/rss", Category.CULTURE),
        source("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml"),
        source("cna-asia", "CNA · Asia", "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml&category=6511", region = Region.ASIA),
        source("bbc-asia", "BBC · Asia", "https://feeds.bbci.co.uk/news/world/asia/rss.xml", region = Region.ASIA),
        source("bbc-europe", "BBC · Europe", "https://feeds.bbci.co.uk/news/world/europe/rss.xml", region = Region.EUROPE),
        source("bbc-us-canada", "BBC · US & Canada", "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml"),
        source("bbc-africa", "BBC · Africa", "https://feeds.bbci.co.uk/news/world/africa/rss.xml", region = Region.AFRICA),
        source("bbc-latin-america", "BBC · Latin America", "https://feeds.bbci.co.uk/news/world/latin_america/rss.xml", region = Region.LATIN_AMERICA),
        source("bbc-middle-east", "BBC · Middle East", "https://feeds.bbci.co.uk/news/world/middle_east/rss.xml", region = Region.MIDDLE_EAST),
        source("bbc-tech", "BBC · Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", Category.TECHNOLOGY),
        source("bbc-science", "BBC · Science & Environment", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", Category.SCIENCE),
        source("bbc-health", "BBC · Health", "https://feeds.bbci.co.uk/news/health/rss.xml", Category.HEALTH),
        source("guardian-china", "Guardian · China", "https://www.theguardian.com/world/china/rss", region = Region.CHINA_MAINLAND),
        source("guardian-taiwan", "Guardian · Taiwan", "https://www.theguardian.com/world/taiwan/rss", region = Region.HONG_KONG_MACAO_TAIWAN),
        source("guardian-australia", "Guardian · Australia", "https://www.theguardian.com/australia-news/rss", region = Region.OCEANIA),
        source("guardian-sport", "Guardian · Sport", "https://www.theguardian.com/sport/rss", Category.SPORTS),
        source("guardian-environment", "Guardian · Environment", "https://www.theguardian.com/environment/rss", Category.ENVIRONMENT),
        source("guardian-science", "Guardian · Science", "https://www.theguardian.com/science/rss", Category.SCIENCE),
        source("hkfp", "Hong Kong Free Press", "https://hongkongfp.com/feed/", region = Region.HONG_KONG_MACAO_TAIWAN),
        source("globalvoices", "Global Voices", "https://globalvoices.org/feed/", Category.SOCIETY),
        source("france24", "France 24 · English", "https://www.france24.com/en/rss"),
        source("dw", "DW · English", "https://rss.dw.com/rdf/rss-en-all"),
        source("japantimes", "Japan Times", "https://www.japantimes.co.jp/feed/", region = Region.ASIA),
        source("cna-latest", "CNA · Latest", "https://www.channelnewsasia.com/api/v1/rss-outbound-feed?_format=xml"),
    )
}
