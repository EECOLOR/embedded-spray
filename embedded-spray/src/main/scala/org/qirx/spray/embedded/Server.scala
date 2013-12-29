package org.qirx.spray.embedded

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.Promise
import scala.util.Random
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.FiniteDuration
import akka.util.Timeout

class Server(
  name: String,
  val host: Host, val port: Port,
  serviceFactory: ServiceFactory,
  idleTimeout: Option[FiniteDuration] = None)(implicit system:ActorSystem) {

  def this(name: String, host: Host, port: Port,
    serviceFactory: ServiceFactory, idleTimeout: FiniteDuration)(implicit system:ActorSystem) =
    this(name, host, port, serviceFactory, Some(idleTimeout))

  val serverName = s"$name-$host-$port"

  private def serverActor =
    new ServerActor(serverName, serviceFactory, idleTimeout.map(Timeout.apply))

  val server = system.actorOf(Props(serverActor), serverName + "-server")

  def start(): Future[Server.Stopped] = {
    val stopped = Promise[Server.Stopped]

    server ! Server.Start(host, port, stopped.success)

    stopped.future
  }

  def stop(): Unit = if (!system.isTerminated) server ! Server.Stop

}

object Server {

  type Stopped = Unit
  case class Start(host: Host, port: Port, onStopped: Unit => Unit)
  case object Stop
  case object IdleTimeout
}