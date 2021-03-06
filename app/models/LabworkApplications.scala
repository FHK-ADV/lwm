package models

import java.util.UUID

import play.api.data.Form
import play.api.data.Forms._
import utils.Global._
import utils.semantic._

import scala.concurrent.Future

case class LabworkApplication(applicant: Resource, labwork: Resource, partners: List[Resource], id: UUID = UUID.randomUUID())

case class LabworkApplicationFormModel(applicant: String, labwork: String, partners: List[String])

case class LabworkApplicationListChangeForm(application: String, labwork: String)

object LabworkApplications {
  object Forms {
    val labworkApplicationForm = Form(
      mapping(
        "applicant" -> nonEmptyText,
        "labwork" -> nonEmptyText,
        "partners" -> list(text)
      )(LabworkApplicationFormModel.apply)(LabworkApplicationFormModel.unapply)
    )
    val labworkApplicationChangeForm = Form(
      mapping(
        "application" -> nonEmptyText,
        "labwork" -> nonEmptyText
      )(LabworkApplicationListChangeForm.apply)(LabworkApplicationListChangeForm.unapply)
    )
  }

  import utils.Global._
  import utils.semantic.Vocabulary._
  import scala.concurrent.Promise
  import scala.concurrent.ExecutionContext.Implicits.global

  def create(application: LabworkApplication): Future[Individual] = {
    val applicationResource = ResourceUtils.createResource(lwmNamespace, application.id)

    def applicationList(labwork: Resource) = {
      val query =
        s"""
             |select ($labwork as ?s) (${lwm.hasApplicationList} as ?p) ?o where {
             | $labwork ${lwm.hasApplicationList} ?o .
             |}
           """.stripMargin

      sparqlExecutionContext.executeQuery(query).map { result ⇒
        SPARQLTools.statementsFromString(result).map(_.o.asResource()).flatten
      }
    }

    val list = applicationList(application.labwork)

    val statements = List(
      Statement(applicationResource, rdf.typ, lwm.LabworkApplication),
      Statement(applicationResource, rdf.typ, owl.NamedIndividual),
      Statement(applicationResource, lwm.hasId, StringLiteral(application.id.toString)),
      Statement(applicationResource, lwm.hasApplicant, application.applicant),
      Statement(application.applicant, lwm.hasPendingApplication, applicationResource),
      Statement(applicationResource, lwm.hasLabWork, application.labwork)
    ) ++ application.partners.map(p ⇒ Statement(applicationResource, lwm.hasPartner, p))

    list.flatMap { al ⇒
      al.headOption.fold(sparqlExecutionContext.executeUpdate(SPARQLBuilder.insertStatements(statements: _*)).map(b ⇒ Individual(applicationResource))) { r ⇒
        sparqlExecutionContext.executeUpdate(SPARQLBuilder.insertStatements(Statement(r, lwm.hasApplication, applicationResource) :: statements: _*)).map(b ⇒ Individual(applicationResource))
      }

    }
  }

  def delete(application: LabworkApplication): Future[LabworkApplication] = {
    val maybeApplication = SPARQLBuilder.listIndividualsWithClassAndProperty(lwm.LabworkApplication, Vocabulary.lwm.hasId, StringLiteral(application.id.toString))
    val resultFuture = sparqlExecutionContext.executeQuery(maybeApplication)
    val p = Promise[LabworkApplication]()
    resultFuture.map { result ⇒
      val resources = SPARQLTools.statementsFromString(result).map(course ⇒ course.s)
      resources.map { resource ⇒
        sparqlExecutionContext.executeUpdate(SPARQLBuilder.removeIndividual(resource)).map { _ ⇒ p.success(application) }
      }
    }
    p.future
  }

  def delete(resource: Resource): Future[Resource] = {
    val p = Promise[Resource]()
    val individual = Individual(resource)
    if (individual.props(rdf.typ).contains(lwm.LabworkApplication)) {
      sparqlExecutionContext.executeUpdate(SPARQLBuilder.removeIndividual(resource)).map { b ⇒ p.success(resource) }
    } else {
      p.failure(new IllegalArgumentException("Resource is not a LabworkApplication"))
    }
    p.future
  }

  def all(): Future[List[Individual]] = {
    sparqlExecutionContext.executeQuery(SPARQLBuilder.listIndividualsWithClass(lwm.LabworkApplication)).map { stringResult ⇒
      SPARQLTools.statementsFromString(stringResult).map(labworkApplication ⇒ Individual(labworkApplication.s)).toList
    }
  }

}
