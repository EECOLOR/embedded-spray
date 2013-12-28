package org.qirx.spray.embedded

import java.net.ServerSocket

object Port {

  def free: Port = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }
}