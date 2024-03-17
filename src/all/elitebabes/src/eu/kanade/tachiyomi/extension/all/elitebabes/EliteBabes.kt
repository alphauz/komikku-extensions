package eu.kanade.tachiyomi.extension.all.elitebabes

import eu.kanade.tachiyomi.multisrc.masonry.ChannelFilter
import eu.kanade.tachiyomi.multisrc.masonry.Masonry
import eu.kanade.tachiyomi.multisrc.masonry.SelectFilter
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response

class EliteBabes : Masonry("Elite Babes", "https://www.elitebabes.com", "all") {
    /**
     * External sites which ref link back to main site.
     * This is like highlight from Channels, without sorting
     * Missing:
     *  - https://www.hegrehub.com
     *  - https://playmatehunter.com
     */
    private val collections: List<Pair<String, String>> = listOf(
        Pair("Default", "-"),
        Pair("SexArt Models", "https://www.sexarthub.com"),
        Pair("Femjoy Models", "https://www.femangels.com"),
        Pair("Playboy Centerfolds", "https://www.centerfoldhunter.com"),
        Pair("Rylsky Art", "https://www.rylskyhunter.com"),
        Pair("Penthouse Sexy Pets", "https://www.penthousehub.com"),
        Pair("Zemani", "https://www.zemanihunter.com"),
        Pair("MetArt Heaven", "https://www.metheaven.com"),
        Pair("MetArt X Models", "https://www.metxhunter.com"),
        Pair("Erotic & Beauty", "https://www.eroticandbeauty.com"),
        Pair("Als Scan", "https://www.alshunter.com"),
        Pair("Digital Desire", "https://www.digitalsweeties.com"),
        Pair("Errotica Babes", "https://www.erroticahunter.com"),
        Pair("MPL Studios", "https://www.mplhunter.com"),
        Pair("Photodromm Models", "https://www.drommhub.com"),
        Pair("W4B - Watch 4 Beauty", "https://www.w4bhub.com"),
        Pair("Domai Erotica", "https://www.domerotica.com"),
        Pair("The Life Erotic", "https://www.tlehunter.com"),
        Pair("Gravure Idols & Asian Glamour", "https://www.jperotica.com"),
        Pair("Nubiles", "https://www.nubileshunter.com"),
        Pair("Goddess Models", "https://www.goddesshunter.com"),
        Pair("Erotic In Mind", "https://www.eroticinmind.com"),
        Pair("Eternal Babes - Desire Girls", "https://www.eternalbabes.com"),
        Pair("Viv Thomas", "https://www.vivhunter.com"),
        Pair("Japanese Beauties", "https://www.jpbeauties.com"),
        Pair("Pinup Models", "https://www.pinuphunter.com"),
        Pair("X-Art", "https://www.xarthub.com"),
        Pair("WoW Girls", "https://www.wowsweeties.com"),
        Pair("Holy Randall", "https://www.randallhub.com"),
        Pair("Gravure Models", "https://www.gravurehunter.com"),
        Pair("Showy Beauty", "https://www.showyhub.com"),
        Pair("Stunning18", "https://www.stunningsweeties.com"),
        Pair("Amour Angels", "https://www.amourhub.com"),
    )

    private class CollectionsFilter(collections: List<Pair<String, String>>) :
        SelectFilter("Collections", collections)

    private var channelsFetchAttempt = 0
    private var channels = emptyList<Pair<String, String>>()

    private fun getChannels() {
        launchIO {
            if (channels.isEmpty() && channelsFetchAttempt < 3) {
                runCatching {
                    channels = listOf(Pair("All channels", "updates")) +
                        client.newCall(GET("$baseUrl/erotic-art-channels/", headers))
                            .execute().asJsoup()
                            .select("ul.list-gallery figure a")
                            .mapNotNull {
                                Pair(
                                    it.select("img").attr("alt"),
                                    it.attr("href")
                                        .removeSuffix("/")
                                        .substringAfterLast("/"),
                                )
                            }
                }
                channelsFetchAttempt++
            }
        }
    }

    override fun getFilterList(): FilterList {
        getChannels()
        val filters = super.getFilterList().list +
            if (channels.isEmpty()) {
                Filter.Header("Press 'reset' to attempt to load channels")
            } else {
                Filter.Header("Channel is ignored when any tag is selected")
                ChannelFilter(channels)
            } +
            listOf(
                Filter.Separator(),
                Filter.Header("Non-default collections ignore other filters"),
                CollectionsFilter(collections),
            )

        return FilterList(filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val collectionsFilter = filters.filterIsInstance<CollectionsFilter>().first()
        return if (collectionsFilter.state == 0) {
            super.searchMangaRequest(page, query, filters)
        } else {
            GET(collectionsFilter.selected, headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()
        return if (collections.map { it.second }.any { requestUrl.matches("^$it".toRegex()) }) {
            /* Handle filter for collections */
            MangasPage(
                mangas = response.asJsoup().select("div.item a[href]:has(img)")
                    .map { element ->
                        SManga.create().apply {
                            setUrlWithoutDomain(element.absUrl("href"))
                            title = element.select("img").attr("alt")
                            thumbnail_url = element.select("img").first()?.imgAttr()
                        }
                    },
                hasNextPage = false,
            )
        } else {
            super.searchMangaParse(response)
        }
    }
}
