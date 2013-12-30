package org.qirx.spray.embedded

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.io.IO
import akka.io.Tcp
import akka.util.Timeout
import spray.can.Http
import spray.http.HttpRequest

class ListenerActor(val name: String, serviceFactory: ServiceFactory, idleTimeout: Option[Timeout]) extends Actor with UnknownMessages {

  import context.system
  import context.dispatcher

  val service = context.actorOf(serviceFactory, name + "-service")

  var scheduledTimeout: Option[Cancellable] = None

  lazy val receive = idle

  lazy val idle = unbound orElse unknown("idle")

  val unbound: Receive = {

    case Listener.Bind(host, port) =>
      become("binding", binding(commander = sender))
      IO(Http) ! Http.Bind(self, host, port)
  }

  def binding(commander: ActorRef): Receive = {

    case Http.Bound(address) =>
      become("bound", bound(commander, listener = sender))
      commander ! Listener.Bound
      scheduleTimeout()

    case unbind @ Listener.Unbind =>
      self ! unbind // queue the message until we are bound
  }

  def bound(commander: ActorRef, listener: ActorRef): Receive = {

    case _: Tcp.Connected =>
      sender ! Tcp.Register(self)

    case Listener.Unbind =>
      become("unbinding", unbinding(commander))
      cancelScheduledTimeout()
      listener ! Http.Unbind

    case Listener.IdleTimeout =>
      self ! Listener.Unbind

    case message: HttpRequest =>
      scheduleTimeout()
      service forward message

    case message =>
      service forward message
  }

  def unbinding(commander: ActorRef): Receive = {

    case Http.Unbound =>
      become("unbound", unbound)
      commander ! Listener.Unbound

    case message: Tcp.ConnectionClosed =>
      service forward message

    case Listener.Unbind =>
    // we are already unbinding, we can ignore this
  }

  def cancelScheduledTimeout() = {
    scheduledTimeout.foreach(_.cancel())
    scheduledTimeout = None
  }

  def scheduleTimeout() =
    idleTimeout.foreach { idleTimeout =>
      cancelScheduledTimeout()
      val timeout = idleTimeout.duration
      scheduledTimeout =
        Some(system.scheduler.scheduleOnce(timeout, self, Listener.IdleTimeout))
    }
}