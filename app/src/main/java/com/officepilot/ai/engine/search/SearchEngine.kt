package com.officepilot.ai.engine.search

import android.util.Log
import com.officepilot.ai.data.remote.DdgApi
import com.officepilot.ai.domain.model.ImageResult
import com.officepilot.ai.domain.model.SearchResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search engine combining DuckDuckGo, Wikipedia, and Wikimedia Commons.
 */
@Singleton
class SearchEngine @Inject constructor(
    private val ddgApi: DdgApi,
    private val httpClient: OkHttpClient
) {
    companion object { private const val TAG = "SearchEngine" }

    /** Web search via DuckDuckGo HTML (no API key needed). */
    suspend fun webSearch(query: String, maxResults: Int = 5): List<SearchResult> {
        return try {
            val resp = ddgApi.search(query)
            if (!resp.isSuccessful) return emptyList()
            val html = resp.body()?.string() ?: return emptyList()
            val doc = Jsoup.parse(html)
            val results = mutableListOf<SearchResult>()
            doc.select(".result__body").take(maxResults).forEach { el ->
                val titleEl = el.select(".result__a").firstOrNull() ?: return@forEach
                val title = titleEl.text().trim()
                var url = titleEl.attr("href").trim()
                if (url.contains("uddg=")) {
                    url = try { java.net.URLDecoder.decode(url.substringAfter("uddg=").substringBefore("&"), "UTF-8") }
                    catch (_: Exception) { url }
                }
                val snippet = el.select(".result__snippet").firstOrNull()?.text()?.trim() ?: ""
                if (title.isNotBlank()) results.add(SearchResult(title, url, snippet))
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e); emptyList()
        }
    }

    /** Fetch readable text from a URL. */
    suspend fun scrapeUrl(url: String, maxChars: Int = 4000): String {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "OfficePilotAI/1.0").build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return ""
            val html = resp.body?.string() ?: return ""
            val doc = Jsoup.parse(html)
            doc.select("script,style,nav,footer,header,aside,iframe,noscript").remove()
            val text = doc.body()?.text() ?: doc.text()
            text.replace(Regex("\\s+"), " ").trim().take(maxChars)
        } catch (e: Exception) {
            Log.e(TAG, "Scrape failed", e); ""
        }
    }

    /** Search Wikipedia for article summary. */
    suspend fun wikiSearch(query: String): String {
        return try {
            val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&prop=extracts" +
                    "&exintro=1&explaintext=1&titles=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val json = resp.body?.string() ?: return ""
            val pages = JsonParser.parseString(json).asJsonObject
                .getAsJsonObject("query")?.getAsJsonObject("pages") ?: return ""
            val page = pages.entrySet().firstOrNull()?.value?.asJsonObject ?: return ""
            page.get("extract")?.asString?.take(3000) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Wiki failed", e); ""
        }
    }

    /** Search Wikimedia Commons for free images. */
    suspend fun searchImages(query: String, maxResults: Int = 5): List<ImageResult> {
        return try {
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                    "&generator=search&gsrsearch=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                    "&gsrnamespace=6&gsrlimit=$maxResults&prop=imageinfo" +
                    "&iiprop=url|size|mime&iiurlwidth=800"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val json = resp.body?.string() ?: return emptyList()
            val pages = JsonParser.parseString(json).asJsonObject
                .getAsJsonObject("query")?.getAsJsonObject("pages") ?: return emptyList()

            pages.entrySet().mapNotNull { (_, pageObj) ->
                val p = pageObj.asJsonObject
                val title = p.get("title")?.asString ?: return@mapNotNull null
                val info = p.getAsJsonArray("imageinfo")?.firstOrNull()?.asJsonObject ?: return@mapNotNull null
                val imgUrl = info.get("url")?.asString ?: return@mapNotNull null
                val thumbUrl = info.get("thumburl")?.asString ?: imgUrl
                val w = info.get("width")?.asInt ?: 0
                val h = info.get("height")?.asInt ?: 0
                // Only include actual images
                val mime = info.get("mime")?.asString ?: ""
                if (!mime.startsWith("image/")) return@mapNotNull null
                ImageResult(title.removePrefix("File:"), imgUrl, thumbUrl, w, h, "Wikimedia Commons")
            }.take(maxResults)
        } catch (e: Exception) {
            Log.e(TAG, "Image search failed", e); emptyList()
        }
    }

    /** Download image bytes from URL. */
    suspend fun downloadImage(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) resp.body?.bytes() else null
        } catch (e: Exception) {
            Log.e(TAG, "Image download failed", e); null
        }
    }
}
