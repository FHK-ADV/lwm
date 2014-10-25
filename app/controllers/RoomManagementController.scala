package controllers

import controllers.LabworkManagementController._
import models._
import play.api.mvc.{ Action, Controller }
import utils.Security.Authentication
import utils.semantic.Vocabulary.{ RDFS, LWM }
import utils.semantic.{ StringLiteral, Individual, Resource }
import utils.Global._
import scala.concurrent.Future

/**
  * Room Management:
  *
  */
object RoomManagementController extends Controller with Authentication {

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  def index() = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      for {
        rooms ← Rooms.all()
      } yield {
        Ok(views.html.room_management(rooms, Rooms.Forms.roomForm, session))
      }
    }
  }

  def roomPost = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      Rooms.Forms.roomForm.bindFromRequest.fold(
        formWithErrors ⇒ {
          for (all ← Rooms.all()) yield {
            BadRequest(views.html.room_management(all.toList, formWithErrors, session))
          }
        },
        room ⇒ {
          Rooms.create(Room(room.roomId, room.name)).map { i ⇒
            Redirect(routes.RoomManagementController.index())
          }
        }
      )
    }
  }

  def roomRemoval() = hasPermissions(Permissions.AdminRole.permissions.toList: _*) {
    session ⇒
      Action.async(parse.json) {
        implicit request ⇒
          val id = (request.body \ "id").as[String]
          Rooms.delete(Resource(id)).map { _ ⇒
            Redirect(routes.RoomManagementController.index())
          }
      }
  }

  def roomEdit(roomId: String) = hasPermissions(Permissions.AdminRole.permissions.toList: _*) {
    session ⇒
      Action.async { implicit request ⇒
        val i = Individual(Resource(roomId))
        Rooms.Forms.roomForm.bindFromRequest.fold(
          formWithErrors ⇒ {
            for (all ← Rooms.all()) yield {
              BadRequest(views.html.room_management(all.toList, formWithErrors, session))
            }
          },
          room ⇒ {
            for {
              id ← i.props(LWM.hasRoomId)
              name ← i.props(LWM.hasName)
            } yield {
              i.update(LWM.hasRoomId, id, StringLiteral(room.roomId))
              i.update(LWM.hasName, name, StringLiteral(room.name))
              i.update(RDFS.label, name, StringLiteral(room.name))
            }
            Future.successful(Redirect(routes.RoomManagementController.index()))
          }
        )
      }
  }
}
