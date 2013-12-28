package org.qirx.spray

import akka.actor.Props
package object embedded {
  type Host = String
  type Port = Int
  type ServiceFactory = Props
}