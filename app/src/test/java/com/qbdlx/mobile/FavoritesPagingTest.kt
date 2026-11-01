package com.qbdlx.mobile

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Qobuz gives at most 500 favorites per request: the library must ask page after page, not stop at 500. */
class FavoritesPagingTest {
    private val server = MockWebServer()
    @After fun stop() { server.shutdown() }

    private fun api(total: Int, offsets: MutableList<Int>): QobuzApi {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val offset = request.requestUrl!!.queryParameter("offset")!!.toInt(); offsets += offset
                val n = minOf(500, total - offset).coerceAtLeast(0)
                val items = (offset until offset + n).joinToString(",") { "{\"name\":\"Artist $it\"}" }
                return MockResponse().setBody("{\"artists\":{\"total\":$total,\"items\":[$items]}}")
            }
        }
        server.start()
        val http = OkHttpClient.Builder().addInterceptor { c ->
            val r = c.request(); val base = server.url("/")
            c.proceed(r.newBuilder().url(r.url.newBuilder().scheme(base.scheme).host(base.host).port(base.port).build()).build())
        }.build()
        return QobuzApi(http).also { it.appId = "x"; it.authToken = "t" }
    }

    @Test fun asksForEveryPageUntilTheTotal() {
        val offsets = ArrayList<Int>()
        assertEquals(1200, api(1200, offsets).favoriteArtists().size)
        assertEquals(listOf(0, 500, 1000), offsets)
    }

    @Test fun exactlyOnePageStopsAfterAnEmptyOrTotalCheck() {
        val offsets = ArrayList<Int>()
        assertEquals(500, api(500, offsets).favoriteArtists().size)
        assertEquals(listOf(0), offsets)
    }

    @Test fun smallListsNeedOneRequest() {
        val offsets = ArrayList<Int>()
        assertEquals(30, api(30, offsets).favoriteArtists().size)
        assertEquals(listOf(0), offsets)
    }
}
