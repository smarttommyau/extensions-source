package eu.kanade.tachiyomi.extension.ja.mangaup

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response


class MangaUp : HttpSource() {
    override val name = "マンガUP"
    override val supportsLatest: Boolean = false // TODO: not yet implemented

    override fun chapterListParse(response: Response): List<SChapter> {
        TODO("Not yet implemented")
    }

    override fun imageUrlParse(response: Response): String {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        TODO("Not yet implemented")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        TODO("Not yet implemented")
    }

    override fun pageListParse(response: Response): List<Page> {
        TODO("Not yet implemented")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    override fun popularMangaRequest(page: Int): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override val baseUrl = "https://www.manga-up.com"

    override val lang = "ja"

}
