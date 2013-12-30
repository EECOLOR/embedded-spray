package org.qirx.spray.embedded

import akka.actor.Actor

trait UnknownMessages { self:Actor =>

  val name:String

  def unknown(state: String): Receive = {
    case unknown =>
      println("==================================================")
      println(s"$name got unknown message when $state: " + unknown)
      println("==================================================")
  }

  def become(state: String, receive: Receive) = context.become(receive orElse unknown(state))
}