package org.qirx.spray.embedded

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import akka.pattern.AskSupport
import akka.util.Timeout
import spray.can.Http

class Server(name: String, idleTimeout: Option[FiniteDuration] = None)(implicit system: ActorSystem) extends AskSupport {

  def this(name: String, serviceFactory: ServiceFactory,
    idleTimeout: FiniteDuration)(implicit system: ActorSystem) =
    this(name, Some(idleTimeout))

  val server =
    system.actorOf(Props(new ServerActor(name, idleTimeout.map(Timeout.apply))))

  def bind(host: Host, port: Port, serviceFactory: ServiceFactory): Listener = {
    implicit val timeout = Timeout(1.second)
    import system.dispatcher

    val listener = (server ? Server.Bind(host, port, serviceFactory)).mapTo[Listener]
    Listener.fromFuture(listener)
  }

  def close(implicit timeout: Timeout): Future[Server.Closed] = {
    import system.dispatcher

    // we still have a problem here, the unbound

    def unbindAll = server ? Server.UnbindAll
    def closeAll = IO(Http) ? Http.CloseAll

    unbindAll.flatMap(_ => closeAll).map(_ => ())
  }
}

object Server {

  type Closed = Unit

  case class Bind(host: Host, port: Port, serviceFactory:ServiceFactory)

  case object UnbindAll
  case object UnboundAll
}