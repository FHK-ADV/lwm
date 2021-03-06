package controllers

import models.{ Substitution, Substitutions }
import play.api.mvc.{ Action, Controller }
import utils.Security.Authentication
import utils.semantic.Resource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

object SubstitutionsManagementController extends Controller with Authentication {
  def index() = hasPermissions(Permissions.AdminRole.permissions.toList: _*) {
    session ⇒
      Action.async {
        implicit request ⇒
          (for {
            substitutions ← Substitutions.all()
          } yield {
            Ok(views.html.substitution_management(session.user, substitutions, Substitutions.Forms.subsitutionForm))
          }).recover {
            case NonFatal(e) ⇒
              InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
          }
      }
  }

  def substitutionPost() = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      Substitutions.Forms.subsitutionForm.bindFromRequest.fold(
        formWithErrors ⇒ {
          for (all ← Substitutions.all()) yield {
            BadRequest(views.html.substitution_management(session.user, all.toList, formWithErrors))
          }
        },
        substitution ⇒ {
          Substitutions.create(Substitution(Resource(substitution.scheduleAssociation), Resource(substitution.substitute))).map { i ⇒
            Redirect(routes.SubstitutionsManagementController.index())
          }
        }
      ).recover {
          case NonFatal(e) ⇒
            InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
        }
    }
  }
}
