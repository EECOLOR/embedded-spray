package org.qirx.spray.embedded

import org.specs2.mutable.Specification
import java.net.ServerSocket
import java.net.BindException

object PortTests extends Specification {

  "Port" should {

    "be able to return a free port" in freePort

  }

  def freePort = {

    lazy val socket1 = new ServerSocket(Port.free)
    lazy val socket2 = new ServerSocket(Port.free)

    try {
      socket1 should not(throwAn[BindException])
      socket2 should not(throwAn[BindException])
    } finally {
      socket1.close()
      socket2.close()
    }
  }
}