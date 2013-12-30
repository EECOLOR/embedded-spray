
import scala.concurrent.duration._
import scala.io.Source

import org.qirx.spray.embedded.Listener
import org.qirx.spray.embedded.Port
import org.qirx.spray.embedded.Server

import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import spray.http.StatusCodes
import spray.http.Uri
import spray.routing.HttpServiceActor
import spray.routing.directives.ContentTypeResolver

object StaticResourceServer extends App {

  implicit val system = ActorSystem("my-actors")

  val port = Port.free
  val host = "localhost"

  val server = new Server("my-server")

  println("Starting server at http://" + host + ":" + port)

  val listener = server.bind(host, port, Props(ResourceService))

  import system.dispatcher

  for {
    _ <- listener.unbound
    _ <- server.close(2.seconds)
  } {
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
          context.parent ! Listener.Unbind
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