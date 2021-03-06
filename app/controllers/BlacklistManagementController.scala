package controllers

import actors.TransactionsLoggerActor.Transaction
import controllers.AdministrationDashboardController._
import models._
import org.joda.time.{ LocalDateTime, Days, Interval, LocalDate }
import play.api.Play
import play.api.libs.concurrent.Akka
import play.api.mvc.{ Action, Controller }
import utils.Global._
import utils.Security.Authentication
import utils.semantic.Vocabulary.{ lwm, rdfs }
import utils.semantic.{ Individual, Resource, SPARQLTools }

import scala.concurrent.Future
import scala.util.control.NonFatal

object BlacklistManagementController extends Controller with Authentication {

  import scala.concurrent.ExecutionContext.Implicits.global
  import Play.current
  val system = Akka.system

  def index() = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      (for {
        blacklists ← Blacklists.all()
        semesterResources ← Semesters.all()
        semesters = semesterResources.map(s ⇒ Individual(s))
      } yield {
        Ok(views.html.blacklist_management(blacklists, semesters, Blacklists.Forms.blacklistForm))
      }).recover {
        case NonFatal(e) ⇒
          InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
      }
    }
  }

  def blacklistEdit(id: String) = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      import utils.Global._
      val query =
        s"""
          |select ?s (${rdfs.label} as ?p) ?o where{
          | <$id> ${lwm.hasSemester} ?s .
          | ?s ${rdfs.label} ?o .
          |}
        """.stripMargin

      sparqlExecutionContext.executeQuery(query).flatMap { result ⇒
        BlacklistDates.getAll(Resource(id)).map { dates ⇒
          val title = SPARQLTools.statementsFromString(result).head.o.asLiteral().get.decodedString

          val semester = Individual(SPARQLTools.statementsFromString(result).head.s.asResource().get)
          val blacklist = Individual(Resource(id))

          Ok(views.html.blacklist_date_management(title, semester, dates, blacklist, Blacklists.Forms.blacklistDateForm, Blacklists.Forms.blacklistDateRangeForm))
        }
      }.recover {
        case NonFatal(e) ⇒
          InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
      }
    }
  }

  def blacklistPost = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      Blacklists.Forms.blacklistForm.bindFromRequest.fold(
        formWithErrors ⇒ {
          for {
            blacklists ← Blacklists.all()
            semesterResources ← Semesters.all()
            semesters = semesterResources.map(s ⇒ Individual(s))
          } yield {
            BadRequest(views.html.blacklist_management(blacklists, semesters, formWithErrors))
          }
        },
        blacklist ⇒ {
          Blacklists.create(Blacklist(Resource(blacklist.semesterResource))).map { i ⇒
            system.eventStream.publish(Transaction(session.user, LocalDateTime.now(), ModifyAction(i.uri, s"New Backlist created by ${session.user}.")))
            Redirect(routes.BlacklistManagementController.index())
          }
        }
      ).recover {
          case NonFatal(e) ⇒
            InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
        }
    }
  }

  def blacklistDelete = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async(parse.json) {
      implicit request ⇒
        val id = (request.body \ "id").as[String]
        (for (dates ← BlacklistDates.getAll(Resource(id))) yield {
          dates.foreach(d ⇒ BlacklistDates.delete(d.uri))
          Blacklists.delete(Resource(id))
          system.eventStream.publish(Transaction(session.user, LocalDateTime.now(), DeleteAction(Resource(id), s"Backlist deleted by ${session.user}.")))
          Redirect(routes.BlacklistManagementController.index())
        }).recover {
          case NonFatal(e) ⇒
            InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
        }
    }
  }

  def blacklistDatePost = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      Blacklists.Forms.blacklistDateForm.bindFromRequest.fold(
        formWithErrors ⇒ {
          for {
            all ← Blacklists.all()
            semesters ← Semesters.all()
            dates ← BlacklistDates.getAll(Resource(formWithErrors.data("blacklistResource")))
          } yield {
            val blacklist = Individual(Resource(formWithErrors.data("blacklistResource")))
            val semester = Individual(Resource(formWithErrors.data("semesterResource")))

            val query =
              s"""
              |select ?s (${rdfs.label} as ?p) ?o where{
              | ${semester.uri} ${rdfs.label} ?o .
              |}
            """.stripMargin

            val result = sparqlExecutionContext.executeQueryBlocking(query)
            val title = SPARQLTools.statementsFromString(result).head.o.asLiteral().get.decodedString

            BadRequest(views.html.blacklist_date_management(title, semester, dates, blacklist, formWithErrors, Blacklists.Forms.blacklistDateRangeForm))

          }
        },
        blacklist ⇒ {
          BlacklistDates.create(BlacklistDate(Resource(blacklist.blacklistResource), blacklist.date)).map { i ⇒
            system.eventStream.publish(Transaction(session.user, LocalDateTime.now(), CreateAction(i.uri, s"Backlist Entry for ${blacklist.date} created by ${session.user}.")))
            Redirect(routes.BlacklistManagementController.blacklistEdit(blacklist.blacklistResource))
          }
        }
      ).recover {
          case NonFatal(e) ⇒
            InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
        }
    }
  }

  def blacklistDateRangePost = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async { implicit request ⇒
      Blacklists.Forms.blacklistDateRangeForm.bindFromRequest.fold(
        formWithErrors ⇒ {
          for {
            all ← Blacklists.all()
            semesters ← Semesters.all()
            dates ← BlacklistDates.getAll(Resource(formWithErrors.data("blacklistResource")))
          } yield {
            val blacklist = Individual(Resource(formWithErrors.data("blacklistResource")))
            val semester = Individual(Resource(formWithErrors.data("semesterResource")))

            val query =
              s"""
              |select ?s (${rdfs.label} as ?p) ?o where{
              | ${semester.uri} ${rdfs.label} ?o .
              |}
            """.stripMargin

            val result = sparqlExecutionContext.executeQueryBlocking(query)
            val title = SPARQLTools.statementsFromString(result).head.o.asLiteral().get.decodedString

            BadRequest(views.html.blacklist_date_management(title, semester, dates, blacklist, Blacklists.Forms.blacklistDateForm, Blacklists.Forms.blacklistDateRangeForm))

          }
        },
        blacklist ⇒ {
          val dates = for (d ← 0 to Days.daysBetween(blacklist.startDate, blacklist.endDate).getDays) yield blacklist.startDate.plusDays(d)

          Future.sequence(dates.map { date ⇒

            BlacklistDates.create(BlacklistDate(Resource(blacklist.blacklistResource), date)).map { ind ⇒
              system.eventStream.publish(Transaction(session.user, LocalDateTime.now(), CreateAction(ind.uri, s"Backlist Entry for $date created by ${session.user}.")))
              ind
            }
          }).map { i ⇒
            Redirect(routes.BlacklistManagementController.blacklistEdit(blacklist.blacklistResource))
          }
        }
      ).recover {
          case NonFatal(e) ⇒
            InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
        }
    }
  }

  def blacklistDateDelete = hasPermissions(Permissions.AdminRole.permissions.toList: _*) { session ⇒
    Action.async(parse.json) { implicit request ⇒
      val listId = (request.body \ "listId").asOpt[String]
      val dateId = (request.body \ "dateId").asOpt[String]

      val maybeDeleted = for {
        list ← listId
        date ← dateId
      } yield BlacklistDates.delete(Resource(date))

      (maybeDeleted match {
        case Some(deleted) ⇒ deleted.map { id ⇒
          system.eventStream.publish(Transaction(session.user, LocalDateTime.now(), DeleteAction(id, s"Backlist Entry deleted by ${session.user}.")))
          Redirect(routes.BlacklistManagementController.blacklistEdit(listId.get))
        }
        case None ⇒ Future(Redirect(routes.BlacklistManagementController.index()))
      }).recover {
        case NonFatal(e) ⇒
          InternalServerError(s"Oops. There seems to be a problem ($e) with the server. We are working on it!")
      }
    }
  }
}
