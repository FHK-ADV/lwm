package controllers

import controllers.SessionManagement._
import models.{ ScheduleAssociations, LabWorks }
import play.api.libs.json.{ JsString, JsObject }
import play.api.mvc.{ Result, Action, Controller }
import play.libs.Akka
import utils.Security.Authentication
import utils.TransactionSupport
import utils.semantic._
import utils.semantic.Vocabulary.{ rdf, lwm }
import utils.Global._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Promise, Future }
import scala.util.control.NonFatal

object StudentInformationController extends Controller with Authentication with TransactionSupport {

  import play.api.Play.current

  override val system = Akka.system()

  def showInformation(id: String) = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      Future {
        val html = views.html.student_information_overview(Resource(id), ScheduleAssociations.Forms.alternateForm)
        Ok(html)
      }.recover {
        case NonFatal(e) ⇒
          InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
      }
    }
  }

  def postAlternateDate(id: String) = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async(parse.json) { implicit request ⇒
      val p = Promise[Result]()
      val os = (request.body \ "oldSchedule").as[String]
      val ns = (request.body \ "schedule").as[String]
      val student = (request.body \ "student").as[String]

      val schedule = Individual(Resource(os))
      val newSchedule = Resource(ns)
      schedule.props.get(lwm.hasAlternateScheduleAssociation) match {
        case None ⇒
          schedule.add(lwm.hasAlternateScheduleAssociation, newSchedule)
          modifyTransaction(session.user, schedule.uri, s"Alternate Schedule entry added to ${schedule.uri} of Student $student by ${session.user}")
          p.success(Ok(JsObject(Seq(
            "status" -> JsString("ok")
          ))))
        case Some(list) ⇒
          if (list.size > 0) {
            list.map { l ⇒
              schedule.remove(lwm.hasAlternateScheduleAssociation, l)
            }
            schedule.add(lwm.hasAlternateScheduleAssociation, newSchedule)
            p.success(Ok(JsObject(Seq(
              "status" -> JsString("ok")
            ))))
          } else {
            schedule.add(lwm.hasAlternateScheduleAssociation, newSchedule)
            p.success(Ok(JsObject(Seq(
              "status" -> JsString("ok")
            ))))
          }
          modifyTransaction(session.user, schedule.uri, s"Alternate Schedule entry added to ${schedule.uri} of Student $student by ${session.user}")
      }

      p.future.recover {
        case NonFatal(e) ⇒
          InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
      }
    }
  }

  def removeAlternateDate(scheduleId: String) = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action(parse.json) {
      implicit request ⇒
        import utils.Implicits._

        val student = (request.body \ "student").asOpt[String]
        val oldSchedule = (request.body \ "oldSchedule").asOpt[String]
        val alternate = (request.body \ "schedule").asOpt[String]

        if (student.isDefined && oldSchedule.isDefined && alternate.isDefined) {
          s"""
              |prefix lwm: <http://lwm.gm.fh-koeln.de/>
              |prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |
              |delete data {
              |  <${student.get}> lwm:hasSchedule <${oldSchedule.get}> .
              |  <${oldSchedule.get}> lwm:hasAlternateScheduleAssociation <${alternate.get}> .
              |}
          """.stripMargin.execUpdate()
          Ok("Schedule removed")
        } else {
          Ok("Schedule not removed")
        }
    }
  }

  def possibleAlternateDates(sId: String, lId: String) = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒

    Action.async(parse.json) { implicit request ⇒
      val maybeGroup = (request.body \ "group").asOpt[String]
      val maybeGroupId = (request.body \ "gId").asOpt[String]
      val maybeOrderId = (request.body \ "oId").asOpt[String]

      if (maybeGroup.isDefined && maybeGroupId.isDefined && maybeOrderId.isDefined) {
        val alternateDates = ScheduleAssociations.getAlternateDates(Resource(maybeGroup.get), maybeGroupId.get, maybeOrderId.get)
        val json = JsObject(alternateDates.map(tuple ⇒ (tuple._1, JsString(tuple._2))).toSeq)

        Future.successful(Ok(json))
      } else {
        Future.successful(Ok("Keine Alternativen verfügbar"))
      }

    }
  }
}
