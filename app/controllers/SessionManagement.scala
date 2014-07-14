package controllers

import com.typesafe.config.ConfigFactory
import org.apache.commons.codec.digest.DigestUtils
import play.api.libs.concurrent.Promise
import play.api.mvc.{Action, Controller, Security}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Right, Left}


case class Session(id: String)


/**
 * Session Management.
 */
object SessionManagement extends Controller {

  import util.LDAPAuthentication._
  import scala.concurrent.duration._

  private var sessions: Set[Session] = Set.empty

 /*
  * {
  *   user: "bla",
  *   password: "hallo123"
  * }
  */
  def login() = Action.async(parse.json) { request =>
    val user = (request.body \ "user").as[String]
    val password = (request.body \ "password").as[String]
    val timeoutFuture = Promise.timeout("Oops", 15.second)

   val config = play.Configuration.root()
   val DN = config.getString("lwm.bindDN")
   val bindHost = config.getString("lwm.bindHost")
   val bindPort = config.getInt("lwm.bindPort")

    val authFuture = authenticate(user, password, bindHost, bindPort, DN)

    Future.firstCompletedOf(Seq(authFuture, timeoutFuture)).map {
      case Left(message: String) =>
        Unauthorized(message)
      case Right(b: Boolean) =>
        val session = createSessionID(user)
        sessions += session
        Ok("").withSession(
          Security.username -> "user",
          "session" -> session.id
        )
      case t: String => InternalServerError(t)
    }
  }

  def logout() = Action { request =>
    val id = request.session.get("session")

    id match {
      case Some(sid) =>
        if (sessions.exists(_.id == sid)) {
          sessions -= sessions.find(_.id == sid).get
          NoContent.withNewSession
        } else {
          NoContent.withNewSession
        }
      case None =>
        NoContent.withNewSession
    }
  }

  private def createSessionID(user: String): Session = {
    val sessionID = DigestUtils.sha1Hex(s"$user::${System.nanoTime()}")
    Session(sessionID)
  }


}
