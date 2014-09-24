import models.{ Degrees, Courses, Degree }
import utils.semantic.Vocabulary.{ RDFS, LWM }
import utils.semantic.{ StringLiteral, Statement, Individual }

class DegreesSpec extends LWMBaseSpec {
  import utils.Global._

  "Degrees" should {
    "add a new Degree to the ontology" in {
      val degree = Degree("Test Degree 1", "td1")
      val returnValue = Degrees.create(degree)

      whenReady(returnValue) { result ⇒
        result.getClass mustEqual classOf[Individual]
        result.properties must contain(Statement(result.uri, LWM.hasId, StringLiteral(degree.id)))
        result.properties must contain(Statement(result.uri, LWM.hasName, StringLiteral(degree.name)))
        result.properties must contain(Statement(result.uri, RDFS.label, StringLiteral(degree.name)))
        assert(Degrees.exists(degree))
      }

    }

    "remove an existing Degree" in {
      val degree = Degree("Test Degree 1", "td1")
      Degrees.delete(degree)
      assert(!Degrees.exists(degree))
    }

  }
}
