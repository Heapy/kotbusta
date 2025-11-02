package io.heapy.kotbusta.ktor

import io.heapy.kotbusta.ApplicationModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import io.heapy.kotbusta.ktor.routes.activity.getActivityRoute
import io.heapy.kotbusta.ktor.routes.admin.adminRoutes
import io.heapy.kotbusta.ktor.routes.auth.googleOauthRoutes
import io.heapy.kotbusta.ktor.routes.auth.loginRoute
import io.heapy.kotbusta.ktor.routes.auth.logoutRoute
import io.heapy.kotbusta.ktor.routes.books.addBookCommentRoute
import io.heapy.kotbusta.ktor.routes.books.bookSearchRoute
import io.heapy.kotbusta.ktor.routes.books.downloadBookRoute
import io.heapy.kotbusta.ktor.routes.books.getBookByIdRoute
import io.heapy.kotbusta.ktor.routes.books.getBookCommentsRoute
import io.heapy.kotbusta.ktor.routes.books.getBookCoverRoute
import io.heapy.kotbusta.ktor.routes.books.getBooksRoute
import io.heapy.kotbusta.ktor.routes.books.getSimilarBooksRoute
import io.heapy.kotbusta.ktor.routes.books.getStarredBooksRoute
import io.heapy.kotbusta.ktor.routes.books.starBookRoute
import io.heapy.kotbusta.ktor.routes.books.unstarBookRoute
import io.heapy.kotbusta.ktor.routes.comments.deleteCommentRoute
import io.heapy.kotbusta.ktor.routes.comments.updateCommentRoute
import io.heapy.kotbusta.ktor.routes.kindle.createDeviceRoute
import io.heapy.kotbusta.ktor.routes.kindle.deleteDeviceRoute
import io.heapy.kotbusta.ktor.routes.kindle.getDevicesRoute
import io.heapy.kotbusta.ktor.routes.kindle.getSendHistoryRoute
import io.heapy.kotbusta.ktor.routes.kindle.sendToKindleRoute
import io.heapy.kotbusta.ktor.routes.kindle.updateDeviceRoute
import io.heapy.kotbusta.ktor.routes.notes.addOrUpdateNoteRoute
import io.heapy.kotbusta.ktor.routes.notes.deleteNoteRoute
import io.heapy.kotbusta.ktor.routes.staticFilesRoute
import io.heapy.kotbusta.ktor.routes.user.userInfoRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

context(applicationModule: ApplicationModule)
fun Application.configureRouting() {
    routing {
        staticFilesRoute()
        loginRoute()
        logoutRoute()
        googleOauthRoutes()

        route("/api") {
            authenticate("auth-session") {
                adminRoutes()
                userInfoRoute()
                getBooksRoute()
                bookSearchRoute()
                getBookByIdRoute()
                getSimilarBooksRoute()
                getBookCoverRoute()
                getBookCommentsRoute()
                getActivityRoute()
                getStarredBooksRoute()
                starBookRoute()
                unstarBookRoute()
                addBookCommentRoute()
                updateCommentRoute()
                deleteCommentRoute()
                addOrUpdateNoteRoute()
                deleteNoteRoute()
                downloadBookRoute()
                getDevicesRoute()
                createDeviceRoute()
                updateDeviceRoute()
                deleteDeviceRoute()
                sendToKindleRoute(CoroutineScope(Dispatchers.IO))
                getSendHistoryRoute()
            }
        }
    }
}
