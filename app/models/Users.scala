package models

import play.api.data.Forms._
import play.api.data._
import utils.semantic._

import scala.concurrent.{Promise, Future}

object UserForms {
  val loginForm = Form(
    mapping(
      "user" -> nonEmptyText,
      "password" -> nonEmptyText
    )(LoginData.apply)(LoginData.unapply)
  )

  val studentForm = Form(
    mapping(
      "id" -> nonEmptyText,
      "firstname" -> text,
      "lastname" -> text,
      "registrationNumber" -> text,
      "email" -> email,
      "phone" -> text,
      "degree" -> text
    )(Student.apply)(Student.unapply)
  )

  val userForm = Form(
    mapping(
      "id" -> nonEmptyText,
      "firstname" -> text,
      "lastname" -> text,
      "email" -> email,
      "phone" -> text
    )(User.apply)(User.unapply)
  )
}

case class LoginData(user: String, password: String)

case class User(  id: String,
                  firstname: String, lastname: String,
                  email: String,
                  phone: String)

object Users{
  import utils.Global._
  import utils.semantic.Vocabulary._

  import scala.concurrent.ExecutionContext.Implicits.global


  def create(user: User): Future[Individual] = {
    val resource = ResourceUtils.createResource(lwmNamespace)
    val statements = List(
      Statement(resource, RDF.typ, LWM.User),
      Statement(resource, RDF.typ, OWL.NamedIndividual),
      Statement(resource, LWM.hasGmId, Literal(user.id)),
      Statement(resource, FOAF.firstName, Literal(user.firstname)),
      Statement(resource, FOAF.lastName, Literal(user.lastname)),
      Statement(resource, RDFS.label, Literal(s"${user.firstname} ${user.lastname}")),
      Statement(resource, NCO.phoneNumber, Literal(user.phone)),
      Statement(resource, FOAF.mbox, Literal(user.email)),
      Statement(resource, RDFS.label, Literal(s"${user.firstname} ${user.lastname}"))
    )
    sparqlExecutionContext.executeUpdate(SPARQLBuilder.insertStatements(lwmGraph, statements: _*)).map{r =>
      Individual(resource)
    }    
  }

  def delete(user: User): Future[User] = {
    val maybeUser = SPARQLBuilder.listIndividualsWithClassAndProperty(LWM.User, Vocabulary.LWM.hasGmId, Literal(user.id))
    val resultFuture = sparqlExecutionContext.executeQuery(maybeUser)
    val p = Promise[User]()
    resultFuture.map{result =>
      val resources = SPARQLTools.statementsFromString(result).map(u => u.s)
      resources.map{resource =>
        sparqlExecutionContext.executeUpdate(SPARQLBuilder.removeIndividual(resource, lwmGraph)).map{_ => p.success(user)}
      }
    }
    p.future
  }

  def delete(resource: Resource): Future[Resource] =  {
    val p = Promise[Resource]()
    val individual = Individual(resource)
    if(individual.props(RDF.typ).contains(LWM.User)){
      sparqlExecutionContext.executeUpdate(SPARQLBuilder.removeIndividual(resource, lwmGraph)).map{b => p.success(resource)}
    }
    p.future
  }

  def all(): Future[List[Individual]] = {
    sparqlExecutionContext.executeQuery(SPARQLBuilder.listIndividualsWithClass(LWM.User)).map{stringResult =>
      SPARQLTools.statementsFromString(stringResult).map(user => Individual(user.s)).toList
    }
  }

  def exists(uid: String): Future[Boolean] = sparqlExecutionContext.executeBooleanQuery(s"ASK {?s ${Vocabulary.LWM.hasGmId} ${Literal(uid).toQueryString}}")
}