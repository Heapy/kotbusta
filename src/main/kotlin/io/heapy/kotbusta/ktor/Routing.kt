package io.heapy.kotbusta.ktor

import io.heapy.kotbusta.ApplicationModule
import io.heapy.kotbusta.ktor.routes.admin.adminRoutes
import io.heapy.kotbusta.ktor.routes.auth.googleOauthRoutes
import io.heapy.kotbusta.ktor.routes.auth.loginRoute
import io.heapy.kotbusta.ktor.routes.auth.logoutRoute
import io.heapy.kotbusta.ktor.routes.healthMetricsRoutes
import io.heapy.kotbusta.ktor.routes.books.downloadBookRoute
import io.heapy.kotbusta.ktor.routes.books.getBookByIdRoute
import io.heapy.kotbusta.ktor.routes.books.getBookContentRoute
import io.heapy.kotbusta.ktor.routes.books.getBookCoverRoute
import io.heapy.kotbusta.ktor.routes.books.getBookTocRoute
import io.heapy.kotbusta.ktor.routes.books.getBooksRoute
import io.heapy.kotbusta.ktor.routes.books.getSimilarBooksRoute
import io.heapy.kotbusta.ktor.routes.books.searchBookContentRoute
import io.heapy.kotbusta.ktor.routes.kindle.createDeviceRoute
import io.heapy.kotbusta.ktor.routes.kindle.deleteDeviceRoute
import io.heapy.kotbusta.ktor.routes.kindle.getDevicesRoute
import io.heapy.kotbusta.ktor.routes.kindle.getSendHistoryRoute
import io.heapy.kotbusta.ktor.routes.kindle.sendToKindleRoute
import io.heapy.kotbusta.ktor.routes.kindle.updateDeviceRoute
import io.heapy.kotbusta.ktor.routes.search.searchBooksRoute
import io.heapy.kotbusta.ktor.routes.staticFilesRoute
import io.heapy.kotbusta.ktor.routes.user.userInfoRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Application.configureRouting() {
    routing {
        staticFilesRoute()
        healthMetricsRoutes()
        loginRoute()
        logoutRoute()
        googleOauthRoutes()

        route("/api") {
            authenticate("auth-session") {
                adminRoutes()
                userInfoRoute()
                searchBooksRoute()
                getBooksRoute()
                getBookByIdRoute()
                getSimilarBooksRoute()
                getBookCoverRoute()
                getBookContentRoute()
                getBookTocRoute()
                searchBookContentRoute()
                downloadBookRoute()
                getDevicesRoute()
                createDeviceRoute()
                updateDeviceRoute()
                deleteDeviceRoute()
                sendToKindleRoute()
                getSendHistoryRoute()
            }
        }
    }
}
