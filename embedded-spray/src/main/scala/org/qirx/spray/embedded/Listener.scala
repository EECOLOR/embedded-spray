package org.qirx.spray.embedded

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise

trait Listener {

  val bound: Future[Listener.Bound]
  val unbound: Future[Listener.Unbound]

  def unbind(): Unit
}

object Listener {
  type Unbound = Unit
  type Bound = Unit

  case class Bind(host: Host, port: Port)
  case object Unbind
  case object Bound
  case object Unbound
  case object IdleTimeout

  def fromFuture(futureListener: Future[Listener])(implicit executor: ExecutionContext): Listener = {

    val _bound = Promise[Listener.Bound]
    val _unbound = Promise[Listener.Unbound]

    futureListener.onSuccess {
      case listener =>
        _bound.completeWith(listener.bound)
        _unbound.completeWith(listener.unbound)
    }

    new Listener {
      val bound = _bound.future
      val unbound = _unbound.future

      def unbind(): Unit =
        futureListener.onSuccess {
          case listener => listener.unbind()
        }
    }
  }
}