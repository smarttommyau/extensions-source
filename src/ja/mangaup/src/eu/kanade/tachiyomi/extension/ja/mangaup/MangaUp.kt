package eu.kanade.tachiyomi.extension.ja.mangaup

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
class MangaUp : HttpSource() {
    override val name = "マンガUP"
    override val supportsLatest: Boolean = true
    override val baseUrl = "https://www.manga-up.com"
    override val lang = "ja"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series", headers)
    }
    override fun popularMangaParse(response: Response): MangasPage {
        val mySManga = response.asJsoup().selectFirst("main")!!.select("section").select("a").map {
            SManga.create().apply {
                title = it.selectFirst("div")!!.text()
                url = it.attr("href")
                thumbnail_url = it.absUrl(it.select("img").attr("srcSet").substringBefore(";"))
            }
        }
        return MangasPage(mySManga, false)
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun chapterListParse(response: Response): List<SChapter> {
        val titleId = response.request.url.toString().substringAfter("titles/").substringBefore("/")
        val mySChapterList = mutableListOf<SChapter>()
        response.asJsoup().select("script").forEach {
            if (it.data().contains("subName")) {
                val data = it.data().substringAfter("chapters\\\"").substringAfter("[").substringBefore("]")
                data.split("},").forEach { ch ->
                    val chapter = SChapter.create().apply {
                        name = ch.substringAfter("name").substringBefore(",").replace(Regex("[\"{}:]"), "").replace("\\", "")
                        url = "$baseUrl/titles/$titleId/chapters/${ch.substringAfter("id").substringBefore(",").replace(Regex("[\"{}:]"), "").replace("\\","")}"
                        chapter_number = 1f
                        date_upload = 0
                    }
                    mySChapterList.add(chapter)
                }
            }
        }
        return mySChapterList
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup().selectFirst("main")!!.selectFirst("section")!!

        return SManga.create().apply {
            title = document.selectFirst("img")!!.attr("alt")
            document.selectFirst("div > div > div")!!.select("div")[1].children().forEach {
                when {
                    it.text().contains("原作") || it.text().contains("者")
                    -> {
                        author = if (author.isNullOrEmpty()) {
                            it.select("div")[1].text().substringAfter("：").substringBefore("（")
                        } else {
                            author + ", " + it.select("div")[1].text().substringAfter("：").substringBefore("（")
                        }
                    }
                    it.text().contains("漫画") || it.text().contains("著者") || it.text().contains("作画") || it.text().contains("原案")
                    -> {
                        artist = if (artist.isNullOrEmpty()) {
                            it.select("div")[1].text().substringAfter("：").substringBefore("（")
                        } else {
                            artist + ", " + it.select("div")[1].text().substringAfter("：").substringBefore("（")
                        }
                    }
                }
            }
            description = document.select("div").last()!!.text()
            document.select("a").forEach {
                genre += it.text() + ", "
            }
            genre = genre?.substringBeforeLast(",")
            document.selectFirst(".text-on_background_high")?.text()!!.let {
                status = if (it.contains("完結") || it.contains("読み切り")) {
                    SManga.COMPLETED
                } else {
                    SManga.ONGOING
                }
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.asJsoup().selectFirst(".fullscreen")!!.select("img").mapIndexed { i, img ->
            Log.i("page", img.attr("src").replace("blob:", ""))
            Page(i, imageUrl = img.attr("src").replace("blob:", ""))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        TODO("Not yet implemented")
        val searchUrl = "$baseUrl/titles".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .toString()
        return GET(searchUrl, headers)
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val mySManga = response.asJsoup().body().select("section")[1].selectFirst("div > div")!!.select("a").map {
            SManga.create().apply {
                title = it.selectFirst("div")!!.text()
                url = it.attr("href")
                thumbnail_url = baseUrl + it.select("img").attr("srcSet").substringBefore(";").substringBefore(" ")
                Log.i("search", "title: $title, url: $url, thumbnail_url: $thumbnail_url")
            }
        }
        return MangasPage(mySManga, false)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
    companion object {
        private val categories = arrayOf(
            "All",
        )
        private val sortBy = arrayOf(
            "New",
        )
    }
}
