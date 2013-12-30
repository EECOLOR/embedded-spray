package org.qirx.spray.embedded

import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.util.Timeout

class ServerActor(val name: String, idleTimeout: Option[Timeout]) extends Actor with UnknownMessages {

  private def newListenerActor(name: String, serviceFactory:ServiceFactory) =
    new ListenerActor(name, serviceFactory, idleTimeout)

  var listeners = Map.empty[ActorRef, ServerActor.ListenerBridge]

  def receive: Receive = available orElse unknown("available")

  val available: Receive = {
    case Server.Bind(host, port, serviceFactory) =>
      val listener = createAndBindListener(host, port, serviceFactory)
      sender ! listener

    case Listener.Bound =>
      completeBindOf(sender)

    case Listener.Unbound =>
      completeUnbindOf(sender)

    case Terminated(listenerActor) =>
      completeRemovalOf(listenerActor)

    case Server.UnbindAll =>
      val commander = sender
      if (listeners.isEmpty) commander ! Server.UnboundAll
      else {
        listeners.values.foreach(_.unbind)
        become("unbindingAll", unbindingAll(commander))
      }
  }

  def unbindingAll(commander: ActorRef): Receive = {
    case Listener.Unbound =>
      completeUnbindOf(sender)
      if (listeners.isEmpty) commander ! Server.UnboundAll

    case Terminated(listenerActor) =>
      completeRemovalOf(listenerActor)
      if (listeners.isEmpty) commander ! Server.UnboundAll
  }

  private def createAndBindListener(host: Host, port: Port, serviceFactory:ServiceFactory) = {
    val listenerName = s"$name-$host-$port"
    val listenerActor = context.actorOf(Props(newListenerActor(listenerName, serviceFactory)), listenerName + "-listener")

    val listener = new ServerActor.ListenerBridge(listenerActor)
    listeners += listenerActor -> listener
    context.watch(listenerActor)

    listenerActor ! Listener.Bind(host, port)

    listener
  }

  private def completeBindOf(listenerActor: ActorRef) = {
    val listenerActor = sender
    val listener = listeners.get(listenerActor)
    listener.foreach(_.completeBind())
  }

  private def completeUnbindOf(listenerActor: ActorRef) = {
    val listener = listeners.get(listenerActor)
    listener.foreach(_.completeUnbind())
    listeners -= listenerActor
    context.unwatch(listenerActor)
    context.stop(listenerActor)
  }

  private def completeRemovalOf(listenerActor: ActorRef) = {
    val listener = listeners.get(listenerActor)
    for (listener <- listener) {
      if (!listener.bound.isCompleted)
        listener.failBind()
      if (!listener.unbound.isCompleted)
        listener.failUnbind()
    }
    listeners -= listenerActor
  }
}

object ServerActor {
  case object UnexpectedFailure extends RuntimeException

  class ListenerBridge(listenerActor: ActorRef) extends Listener {
    private val _bound = Promise[Listener.Bound]
    private val _unbound = Promise[Listener.Unbound]

    def completeBind() = _bound.complete(Success())
    def completeUnbind() = _unbound.complete(Success())
    def failBind() = _bound.complete(Failure(UnexpectedFailure))
    def failUnbind() = _unbound.complete(Failure(UnexpectedFailure))

    val bound = _bound.future
    val unbound = _unbound.future
    def unbind =
      if (!unbound.isCompleted) listenerActor ! Listener.Unbind
  }
}