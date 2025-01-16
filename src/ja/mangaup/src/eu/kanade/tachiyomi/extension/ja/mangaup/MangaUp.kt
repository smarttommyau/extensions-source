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
                        name = ch.substringAfter("subName").substringBefore(",").replace(Regex("[\"{}:]"), "").replace("\\", "")
                        name = (name.isNullOrEmpty()?"":(name+" - "))+ ch.substringAfter("name").substringBefore(",").replace(Regex("[\"{}:]"), "").replace("\\", "")
                        if(ch.substringAfter("status").substringBefore(",").contains("1")){
                            name = name + " [VIP]"
                        }
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
        TODO("Deal with vip chapters, login get image from app api")
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
                            .substringBefore("\\\"")
                            .replace("\u0026","&")
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
        // TopGenreGroup(), // /top/genres is same as /genres
        DayGroup(),// series
        GenresGroup(),
        RankingsGroup(),
        TitlesThemesGroup(), // titles?query stuffs
        TitlesMagazineGroup(),
    )

    private class DayGroup : UriPartFilter(
        "連載一覧",
        arrayOf(
            Pair("",""), //None
            Pair("月曜日","/series/mon"),
            Pair("火曜日","/series/tue"),
            Pair("水曜日","/series/wed"),
            Pair("木曜日","/series/thu"),
            Pair("金曜日","/series/fri"),
            Pair("土曜日","/series/sat"),
            Pair("日曜日","/series/sun"),
            Pair("完","/series/end"),
            Pair("読切","/series/yomikiri")
        )
    )

    private class GenresGroup : UriPartFilter(
        "ジャンル一覧",
        arrayOf(
            Pair("",""), //None
            Pair("少年","/genres/2"),
            Pair("少女","/genres/41"),
            Pair("青年","/genres/44"),
            Pair("女性","/genres/50"),
            Pair("アニメ化・実写化","/genres/5"),
            Pair("バトル・格闘・アクション","/genres/8"),
            Pair("ファンタジー・幻想","/genres/11"),
            Pair("4コマ","/genres/14"),
            Pair("学園","/genres/17"),
            Pair("エッセイ・日常・ほのぼの","/genres/20"),
            Pair("ギャグ・コメディ","/genres/23"),
            Pair("サスペンス","/genres/26"),
            Pair("推理・ミステリー","/genres/29"),
            Pair("ラブコメ","/genres/32"),
            Pair("萌え系","/genres/35"),
            Pair("怪奇・ホラー","/genres/38"),
            Pair("ギャンブル","/genres/47"),
            Pair("歴史・時代劇","/genres/53"),
            Pair("ラブストーリー","/genres/56"),
            Pair("職業・ビジネス","/genres/59"),
            Pair("戦争・軍事・戦記","/genres/62"),
            Pair("スポーツ","/genres/65"),
            Pair("ヒューマンドラマ","/genres/68"),
            Pair("ヤンキー・極道","/genres/71"),
            Pair("動物・ペット・植物","/genres/74"),
            Pair("アンソロジー・短編集","/genres/77"),
            Pair("SF","/genres/86"),
            Pair("教養・学習","/genres/89"),
            Pair("料理・グルメ","/genres/92"),
            Pair("実話・体験","/genres/95"),
            Pair("ガールズラブ","/genres/98"),
            Pair("旅行・紀行","/genres/101"),
            Pair("異世界・転生","/genres/107"),
            Pair("日常・ほのぼの","/genres/110"),
            Pair("ボーイズラブ","/genres/113"),
            Pair("実話・体験・エッセイ","/genres/116"),
            Pair("お色気","/genres/124"),
            Pair("異世界","/genres/127"),
            Pair("百合","/genres/130"),
            Pair("鬱展開注意","/genres/133"),
            Pair("スローライフ","/genres/136"),
            Pair("転生・転移","/genres/139"),
            Pair("追放","/genres/142"),
            Pair("王族・貴族","/genres/145"),
            Pair("聖女・令嬢","/genres/148"),
            Pair("冒険","/genres/151"),
            Pair("絶望","/genres/154"),
            Pair("読み切り","/genres/157"),
            Pair("実写化・アニメ化","/genres/158")
        )
    )

    private class RankingsGroup : UriPartFilter(
        "ランキング",
        arrayOf(
            Pair("",""), //None
            Pair("総合","/rankings/1"),
            Pair("異世界","/rankings/2"),
            Pair("ちょっとエロ","/rankings/3"),
            Pair("男子向け","/rankings/4"),
            Pair("女子向け","/rankings/4"),
        )
    )

    private class TitlesThemesGroup : UriPartFilter(
        "マンガＵＰ！の宣伝",
        arrayOf(
            Pair("",""), //None
            Pair("広告で見かけたのはもしかしてコレ？","/titles?query=theme.theme_id%3D1"),
            Pair("最新話を毎週読もう！","/titles?query=theme.theme_id%3D638"),
            Pair("追い出されたアイツが最強だった件","/titles?query=theme.theme_id%3D56"),
            Pair("戦慄のデスゲーム","/titles?query=theme.theme_id%3D43"),
            Pair("ココロ踊る恋と青春","/titles?query=theme.theme_id%3D23")
        )
    )

    private class TitlesMagazineGroup : UriPartFilter(
        "雑誌レーベル一覧",
        arrayOf(
            Pair("",""), //None
            Pair("マンガＵＰ！オリジナル","/titles?label_id=2"),
            Pair("ガンガンONLINE","/titles?label_id=3"),
            Pair("ガンガンJOKER","/titles?label_id=5"),
            Pair("ガンガンpixiv","/titles?label_id=6"),
            Pair("Gファンタジー","/titles?label_id=7"),
            Pair("BLiss","/titles?label_id=8"),
            Pair("月刊少年ガンガン","/titles?label_id=9"),
            Pair("ビッグガンガン","/titles?label_id=11"),
            Pair("ヤングガンガン","/titles?label_id=12"),
            Pair("その他","/titles?label_id=1")
        )
     )
}
