
import org.qirx.spray.embedded.Server
import org.qirx.spray.embedded.Port
import spray.routing.HttpServiceActor
import akka.actor.ActorSystem
import akka.actor.Props
import spray.http.Uri
import spray.http.StatusCodes
import spray.routing.directives.ContentTypeResolver
import akka.event.Logging
import scala.io.Source

object StaticResourceServer extends App {

  implicit val system = ActorSystem("my-actors")

  val port = Port.free
  val host = "localhost"

  val server = new Server("my-server", host, port, Props(ResourceService))

  println("Starting server at http://" + host + ":" + port)

  val stopped = server.start()

  import system.dispatcher

  stopped.onComplete { _ =>
    println("Server stopped")
    system.shutdown()
    system.awaitTermination()
  }

}

object ResourceService extends HttpServiceActor {

  val log = Logging(context.system, this)

  def receive = runRoute(route)

  val route =
    pathEndOrSingleSlash {
      redirect("index.html", StatusCodes.PermanentRedirect)
    } ~
      path("stop") {
        complete {
          context.parent ! Server.Stop
          StatusCodes.NoContent
        }
      } ~
      path(Rest) { path =>
        serveResource(path)
      }

  def serveResource(name: String)(implicit resolver: ContentTypeResolver) =
    get {
      respondWithMediaType(resolver(name).mediaType) {
        resourceAsString(name) match {
          case Some(resource) => complete(resource)
          case None =>
            log.warning(s"Could not find resource with name '$name'")
            reject
        }
      }
    }

  def resourceAsString(name: String) = {
    val classLoader = getClass.getClassLoader
    val possibleResourceStream = Option(classLoader.getResourceAsStream(name))

    for (resourceStream <- possibleResourceStream)
      yield Source.fromInputStream(resourceStream).mkString
  }
}