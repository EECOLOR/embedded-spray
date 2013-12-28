package org.qirx.spray.embedded

import org.specs2.mutable.Specification
import akka.actor.Props
import akka.actor.Actor
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import org.specs2.time.NoTimeConversions
import spray.http._
import spray.client.pipelining._
import akka.actor.ActorSystem
import scala.concurrent.Awaitable
import spray.routing.HttpServiceActor
import java.net.BindException
import java.net.ServerSocket
import akka.actor.DeadLetter
import akka.event.Logging
import akka.actor.DeadLetter
import akka.actor.PoisonPill
import akka.io.IO
import spray.can.Http
import com.typesafe.config.ConfigFactory
import org.specs2.matcher.Matcher
import spray.routing.StandardRoute
import spray.routing.Directives
import akka.actor.ActorRef
import akka.actor.ActorContext
import java.util.concurrent.TimeoutException
import scala.util.Random

class ServerTests extends Specification with NoTimeConversions {

  "Server" should {
    "be able to handle requests" in handleRequests
    "open and close sockets correctly" in openAndCloseSockets
    "be able to start on different ports" in multiplePorts
    "automatically stop after the given idle timeout" in stopWhenIdle
  }

  def handleRequests =
    withClient { client =>
      withStartedServer { server =>

        val result = await(client(Get(url(server.port))))
        result.entity.asString === indexResponse
      }
    }

  def openAndCloseSockets =
    withServer { server =>
      //give the actor system some time to start up

      val port = server.port

      checkSocket(port, not(throwA[BindException]))
      val stopped = server.start()
      // make a call, this makes sure the server is started
      sendRequestAndWait(port)
      checkSocket(port, throwA[BindException])
      server.stop()
      await(stopped)

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
    withServer { server1 =>

      withServer { server2 =>

        val stopped1 = server1.start()
        val stopped2 = server2.start()

        sendRequestAndWait(server1.port)
        sendRequestAndWait(server2.port)

        server1.stop()
        await(stopped1)

        sendRequestAndWait(server2.port)

        ok
      }
    }
  }

  def stopWhenIdle = stopWhenIdleAfterRequest and stopWhenIdleAfterStart

  private def stopWhenIdleAfterRequest =
    withServer(idleTimeout = 1.seconds) { server =>

      val stopped = server.start()

      Thread.sleep(300)
      sendRequestAndWait(server.port)
      Thread.sleep(300)
      sendRequestAndWait(server.port)
      Thread.sleep(300)
      sendRequestAndWait(server.port)
      Thread.sleep(300)
      sendRequestAndWait(server.port)

      //being idle

      await(stopped) must not(throwA[TimeoutException])

    }

  private def stopWhenIdleAfterStart =
    withServer(idleTimeout = 1.seconds) { server =>

      val stopped = server.start()

      //being idle

      await(stopped) must not(throwA[TimeoutException])
    }

  private lazy val indexResponse = "This is the index page"

  private def sendRequestAndWait(port: Port) =
    withClient { client =>
      await(sendRequest(client, port))
    }

  private def sendRequest(client: HttpRequest => Future[HttpResponse], port: Port) =
    client(Get(url(port)))

  private def createServer(name: String, idleTimeout: Option[FiniteDuration])(implicit system: ActorSystem) =
    new Server(
      name = name,
      host = "localhost",
      port = Port.free,
      serviceFactory = FakeService(),
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

  private def await[T](f: Future[T]): T = Await.result(f, 2.seconds)

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
      code(server)
    }

  private def withStartedServer[T](code: Server => T) =
    withServer { server =>
      val stopped = server.start()
      try {
        code(server)
      } finally {
        server.stop();
        await(stopped)
      }
    }

  private def withClient[T](code: (HttpRequest => Future[HttpResponse]) => T) =
    withActorSystem("client") { implicit system =>
      import system.dispatcher // execution context for futures

      val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

      code(pipeline)
    }
}
