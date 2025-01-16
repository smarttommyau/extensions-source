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
    // TODO: ConfigurableSource for quality, now default 75
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
        val mySManga = response.asJsoup().selectFirst("main")!!.select("section")[1].select("a").map {
            SManga.create().apply {
                title = it.selectFirst("img").attr("alt")
                url = it.attr("href")
                thumbnail_url = baseUrl + it.selectFirst("img").attr("srcSet").substringBefore(" ")
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

    override fun latestUpdatesRequest(page: Int): Request = {
        return GET("$baseUrl/titles".toHttpUrl().newBuilder()
            .addQueryParameter("query","new_series")
            .toString()
        , headers)
    }
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url, headers)
    }
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup().selectFirst("main")!!.selectFirst("section")!!
        val head = response.asJsoup().selectFirst("head")!!
        return SManga.create().apply {
            title = document.selectFirst("img")!!.attr("alt")
            thumbnail_url = head.selectFirst("meta[property=og:image]")!!.attr("content")
            document.selectFirst("div > div > div")!!.select("div")[1].select("div").forEach {
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
            description = head.selectFirst("meta[property=og:description]")!!.attr("content")
            genre = ""
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
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }
    override fun pageListParse(response: Response): List<Page> {
        Log.i("page", response.asJsoup().html())
        return List<Page>.create().apply{
            response.asJsoup().select("script").forEach{
                if(it.data().contains("mainpage")){
                    it.data().split("mainpage").drop(0).forEach{
                        if(it.contains("image")){
                            val url = it
                            .substringAfter("imageUrl")
                            .substringAfter(":")
                            .substringAfter("\"")
                            .substringBefore("\"")
                            add(Page.create().apply{
                                imageUrl = "$baseUrl/_next/image".toHttpUrl().newBuilder()
                                    .addQueryEncodedQueryParameter("url", url)
                                    .addQueryParameter("w", "1080")
                                    .addQueryParameter("q", "75")
                                    .toString()
                            })
                        }
                    }
                }
            }
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
                thumbnail_url = baseUrl + it.select("img").attr("srcSet").substringBefore(" ")
                Log.i("search", "title: $title, url: $url, thumbnail_url: $thumbnail_url")
            }
        }
        return MangasPage(mySManga, false)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
    override fun getFilterList() = FilterList(
        TopGenreGroup(),
        DayGroup(),// series
        TitlesThemesGroup(),
        TitlesMagazineGroup(),
    )
     private class TitlesMagazineGroup : UriPartFilter(
        "雑誌レーベル",
        arrayOf(
            Pair("マンガＵＰ！オリジナル","/titles?label_id=2"),
            Pair("ガンガンONLINE","/titles?label_id=3"),
            Pair("ガンガンJOKER","/titles?label_id=5"),
            Pair("ガンガンpixiv","/titles?label_id=6"),
            Pair("Gファンタジー","/titles?label_id=7"),
            Pair("BLiss","/titles?label_id=8"),
            Pair("月刊少年ガンガン","/titles?label_id=9"),
            Pair("ビッグガンガン","/titles?label_id=11"),
            Pair("ヤングガンガン","/titles?label_id=12"),
            Pair("その他","/titles?label_id=1"),
        )
     )
}
