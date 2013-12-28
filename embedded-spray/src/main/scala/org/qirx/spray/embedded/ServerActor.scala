package org.qirx.spray.embedded

import akka.actor.Actor
import akka.io.IO
import spray.can.Http
import akka.actor.ActorRef
import akka.io.Tcp
import scala.concurrent.Future
import akka.actor.Cancellable
import akka.util.Timeout
import spray.http.HttpRequest

class ServerActor(name: String, serviceFactory: ServiceFactory, idleTimeout: Option[Timeout]) extends Actor {

  import context.system
  import context.dispatcher

  val service = context.actorOf(serviceFactory, name + "-service")

  var onStoppedCallback: Option[Unit => Unit] = None

  var scheduledTimeout: Option[Cancellable] = None

  lazy val receive = idle

  lazy val idle = stopped orElse unknown("idle")

  val stopped: Receive = {

    case Server.Start(host, port, callback) =>
      onStoppedCallback = Some(callback)
      IO(Http) ! Http.Bind(self, host, port)
      become("starting", starting)
  }

  val starting: Receive = {

    case Http.Bound(address) =>
      scheduleTimeout()
      become("started", started(listener = sender))

    case stop @ Server.Stop =>
      self ! stop // queue the message until we are started
  }

  def started(listener: ActorRef): Receive = {

    case _: Tcp.Connected =>
      sender ! Tcp.Register(self)

    case Server.Stop =>
      listener ! Http.Unbind
      become("stopping", stopping)

    case Server.IdleTimeout =>
      self ! Server.Stop

    case message =>
      scheduleTimeout()
      service forward message
  }

  val stopping: Receive = {

    case Http.Unbound =>
      IO(Http) ! Http.CloseAll

    case Http.ClosedAll =>
      become("stopped", stopped)
      callOnStopped()

    case message:Tcp.ConnectionClosed =>
      service forward message
  }

  def unknown(state: String): Receive = {
    case unknown =>
      println("=================================")
      println(s"ServerActor got unknown message when $state: " + unknown)
      println("=================================")
  }

  def become(state: String, receive: Receive) = context.become(receive orElse unknown(state))

  def scheduleTimeout() = {
    scheduledTimeout.foreach(_.cancel())
    scheduledTimeout = None
    idleTimeout.foreach { idleTimeout =>
      val timeout = idleTimeout.duration
      scheduledTimeout =
        Some(system.scheduler.scheduleOnce(timeout, self, Server.IdleTimeout))
    }
  }

  def callOnStopped() =
    onStoppedCallback.foreach { callback =>
      onStoppedCallback = None
      Future(callback())(system.dispatcher)
    }

}