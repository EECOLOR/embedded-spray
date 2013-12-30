package org.qirx.spray.embedded

import java.net.BindException
import java.net.ServerSocket
import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.Awaitable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorSystem
import akka.actor.Props
import spray.client.pipelining._
import spray.http._
import spray.routing.HttpServiceActor
import spray.routing.StandardRoute
import scala.concurrent.Promise

class ServerTests extends Specification with NoTimeConversions {

  "Server" should {
    "be able to handle requests" in handleRequests
    "open and close sockets correctly" in openAndCloseSockets
    "be able to start on different ports" in multiplePorts
    "automatically stop after the given idle timeout" in stopWhenIdle
    "bind in a loop" in runInLoop
  }

  def handleRequests =
    withClient { client =>
      withBoundListener { port =>

        val result = await(client(Get(url(port))))
        result.entity.asString === indexResponse
      }
    }

  def openAndCloseSockets =
    withServer { server =>
      val port = Port.free

      checkSocket(port, not(throwA[BindException]))
      val listener = bind(server, port)
      // make a call, this makes sure the server is started
      sendRequestAndWait(port)
      checkSocket(port, throwA[BindException])
      listener.unbind()
      await(listener.unbound)

      checkSocket(port, not(throwA[BindException]))
    }

  def checkSocket(port: Port, check: Matcher[Any]) = {
    var socket: Option[ServerSocket] = None

    try {
      { socket = Some(new ServerSocket(port)) } must check
    } finally {
      socket.foreach(_.close)
    }
  }

  def multiplePorts = {
    withServer { server =>

      val (listener1, port1) = bind(server)
      val (listener2, port2) = bind(server)

      sendRequestAndWait(port1)
      sendRequestAndWait(port2)

      listener1.unbind()
      await(listener1.unbound)

      sendRequestAndWait(port2)

      ok
    }
  }

  def stopWhenIdle = stopWhenIdleAfterRequest and stopWhenIdleAfterStart

  private def stopWhenIdleAfterRequest =
    withServer(idleTimeout = 1.seconds) { server =>

      val (listener, port) = bind(server)

      Thread.sleep(300)
      sendRequestAndWait(port)
      Thread.sleep(300)
      sendRequestAndWait(port)
      Thread.sleep(300)
      sendRequestAndWait(port)
      Thread.sleep(300)
      sendRequestAndWait(port)

      //being idle

      await(listener.unbound) must not(throwA[TimeoutException])

    }

  private def stopWhenIdleAfterStart =
    withServer(idleTimeout = 1.seconds) { server =>

      val listener = bind(server, Port.free)

      //being idle

      await(listener.unbound) must not(throwA[TimeoutException])
    }

  def runInLoop = {
    withActorSystem(name = "runInLoop") { implicit system =>

      val server = createServer(system.name, Some(500.milliseconds))
      var results = 0
      for (_ <- 1 to 5) {
        val (listener, port) = bind(server)
        sendRequestAndWait(port)
        sendRequestAndWait(port)
        await(listener.unbound)
        results += 1
      }

      await(server.close(testTimeout))

      results === 5
    }
  }

  private lazy val indexResponse = "This is the index page"

  private def bind(server:Server, port:Port):Listener =
    server.bind("localhost", port, FakeService())

  private def bind(server:Server):(Listener, Port) = {
    val port = Port.free
    (bind(server, port), port)
  }

  private def sendRequestAndWait(port: Port) =
    withClient { client =>
      await(sendRequest(client, port))
    }

  private def sendRequest(client: HttpRequest => Future[HttpResponse], port: Port) =
    client(Get(url(port)))

  private def createServer(name: String, idleTimeout: Option[FiniteDuration])(implicit system: ActorSystem) =
    new Server(
      name = name,
      idleTimeout = idleTimeout)

  private def url(port: Port) =
    s"http://localhost:$port/"

  private class FakeService() extends HttpServiceActor {

    def receive = runRoute(route)

    lazy val route =
      get {
        pathEndOrSingleSlash {
          complete(indexResponse)
        }
      }
  }

  private object FakeService {
    def apply() = Props(new FakeService())
  }

  private val testTimeout = 2.seconds

  private def await[T](f: Future[T]): T = Await.result(f, testTimeout)

  private def withActorSystem[T](name: String)(code: ActorSystem => T) = {

    val configString =
      """|akka.log-dead-letters-during-shutdown=off
         |akka.loglevel=WARNING
         |""".stripMargin

    val config = ConfigFactory.parseString(configString).withFallback(ConfigFactory.load())

    val system = ActorSystem(name + "-system-" + Random.nextInt(Int.MaxValue), config)

    try {
      code(system)
    } finally {
      system.shutdown()
      system.awaitTermination()
    }
  }

  private def withServer[T](code: Server => T): T =
    withServer(None)(code)

  private def withServer[T](idleTimeout: FiniteDuration)(code: Server => T): T =
    withServer(Some(idleTimeout))(code)

  private def withServer[T](idleTimeout: Option[FiniteDuration])(code: Server => T): T =
    withActorSystem(name = "test") { implicit system =>
      val server = createServer(system.name, idleTimeout)
      try {
        code(server)
      } finally {
        await(server.close(testTimeout))
      }
    }

  private def withBoundListener[T](code: Port => T) =
    withServer { server =>
      val (listener, port) = bind(server)
      await(listener.bound)
      code(port)
    }

  private def withClient[T](code: (HttpRequest => Future[HttpResponse]) => T) =
    withActorSystem("client") { implicit system =>
      import system.dispatcher // execution context for futures

      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

      code(pipeline)
    }
}
