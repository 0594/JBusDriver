package  me.jbusdriver.ui.data.magnet

import android.util.Base64
import me.jbusdriver.common.KLog
import me.jbusdriver.mvp.bean.Magnet
import org.jsoup.Jsoup

interface IMagnetLoader {

    /**
     * 是否有下一页
     */
    var hasNexPage: Boolean

    /**
     * 放入后台线程执行
     */
    fun loadMagnets(key: String, page: Int): List<Magnet>

}

const val MagnetFormatPrefix = "magnet:?xt=urn:btih:"

/**  "btso.pw" to BtsoPWMagnetLoaderImpl()
 */
val MagnetLoaders: Map<String, IMagnetLoader> by lazy {
    mapOf("btdiggs" to BtdiggsMagnetLoaderImpl(), "BTSOW" to BTSOWMagnetLoaderImpl(), "Btanv" to BtanvMagnetLoaderImpl())
}


class BTSOWMagnetLoaderImpl : IMagnetLoader {
    //  key -> page
    private val search = "http://www.btsows.com/s/%s/%s/"

    override var hasNexPage: Boolean = true

    override fun loadMagnets(key: String, page: Int): List<Magnet> {
        val doc = Jsoup.connect(search.format(key.trim(), page)).get()
        hasNexPage = doc.select(".pagination").text().contains("next", true)
        return doc.select(".btsowlist .row").map {
            val hrefNode = it.select("a")
            val childs = it.children()
            val size = childs.getOrNull(1)?.text() ?: "未知"
            val date = childs.getOrNull(2)?.text() ?: "未知"
            val href = hrefNode.attr("href")
            val hash = href.split("/").last()
            Magnet(hrefNode.text(), size, date, MagnetFormatPrefix + hash)
        }
    }
}


class BtanvMagnetLoaderImpl : IMagnetLoader {

    private val search = "https://www.btmae.com/ma/%s/time-%s.html"
    override var hasNexPage: Boolean = true

    override fun loadMagnets(key: String, page: Int): List<Magnet> {
        val doc = Jsoup.connect(search.format(key.trim(), page)).get()
        hasNexPage = (doc.select(".bottom-pager").firstOrNull()?.children()?.size ?: 0) > 0
        return doc.select("#content .cili-item").map {
            //it.log()
            val bars = it.select(".item-bar").firstOrNull()?.children()?.map { if (it.tagName() == "a") it.attr("href") else it.text() }
                    ?: emptyList<String>()
            Magnet(it.select(".item-title").text(), bars.find { it.contains("大小") } ?: "未知",
                    bars.find { it.contains("时间") }
                            ?: "未知", bars.find { it.contains(MagnetFormatPrefix) } ?: "")
        }
    }
}


class BtdiggsMagnetLoaderImpl : IMagnetLoader {
    //  key -> page
    private val search = "http://btdiggs.com/search/%s/%s/0/0.html"

    override var hasNexPage: Boolean = true

    private fun encode(string: String) = Base64.encodeToString(string.toByteArray(), Base64.NO_PADDING or Base64.URL_SAFE)

    override fun loadMagnets(key: String, page: Int): List<Magnet> {
        KLog.d("loadMagnets ${search.format(encode(key), page)}")
        val doc = Jsoup.connect(search.format(encode(key).trim(), page)).get()
        hasNexPage = doc.select(".page-split :last-child[title]").size > 0
        return doc.select(".list dl").map {
            val href = it.select("dt a")
            val title = href.text()
            val labels = it.select(".attr span")
            Magnet(title, labels.component2().text(), labels.component1().text(), labels.select("a").attr("href"))
        }

    }
}


//==================================================================================================

/**
 * 当前无法使用
 */
class BtsoPWMagnetLoaderImpl : IMagnetLoader {
    //  key -> page
    private val search = "https://btso.pw/search/%s/page/%s"
    private val headers = mapOf("Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7")

    override var hasNexPage: Boolean = true

    override fun loadMagnets(key: String, page: Int): List<Magnet> {
        val doc = Jsoup.connect(search.format(key.trim(), page)).headers(headers).get()
        hasNexPage = doc.select(".pagination [name=nextpage]").isNotEmpty()
        return doc.select(".data-list [class=row]").map {
            val labels = it.children().map { it.text() }.takeLast(2)
            val href = it.select("a")
            val hash = href.attr("href").split("/").last()
            Magnet(href.attr("title"), labels.first(), labels.last(), MagnetFormatPrefix + hash)
        }

    }
}