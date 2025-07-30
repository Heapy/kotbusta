package io.heapy.kotbusta.config

import io.heapy.kotbusta.ApplicationFactory
import io.heapy.kotbusta.config.routes.activity.getActivityRoute
import io.heapy.kotbusta.config.routes.admin.adminRoutes
import io.heapy.kotbusta.config.routes.auth.googleOauthRoutes
import io.heapy.kotbusta.config.routes.auth.loginRoute
import io.heapy.kotbusta.config.routes.auth.logoutRoute
import io.heapy.kotbusta.config.routes.books.addBookCommentRoute
import io.heapy.kotbusta.config.routes.books.bookSearchRoute
import io.heapy.kotbusta.config.routes.books.downloadBookRoute
import io.heapy.kotbusta.config.routes.books.getBookByIdRoute
import io.heapy.kotbusta.config.routes.books.getBookCommentsRoute
import io.heapy.kotbusta.config.routes.books.getBookCoverRoute
import io.heapy.kotbusta.config.routes.books.getBooksRoute
import io.heapy.kotbusta.config.routes.books.getSimilarBooksRoute
import io.heapy.kotbusta.config.routes.books.getStarredBooksRoute
import io.heapy.kotbusta.config.routes.books.starBookRoute
import io.heapy.kotbusta.config.routes.books.unstarBookRoute
import io.heapy.kotbusta.config.routes.comments.deleteCommentRoute
import io.heapy.kotbusta.config.routes.comments.updateCommentRoute
import io.heapy.kotbusta.config.routes.notes.addOrUpdateNoteRoute
import io.heapy.kotbusta.config.routes.notes.deleteNoteRoute
import io.heapy.kotbusta.config.routes.staticFilesRoute
import io.heapy.kotbusta.config.routes.user.userInfoRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

context(applicationFactory: ApplicationFactory)
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
            }
        }
    }
}
