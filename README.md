Embedded Spray server
=====================

A small wrapper around Spray's http and routing framework.

Installation
------------

``` scala
libraryDependencies += "org.qirx" %% "embedded-spray" % "0.1-SNAPSHOT"

resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"
```

Usage
-----

Create an http service

``` scala
object MyService extends HttpServiceActor {

  def receive = runRoute(route)

  val route = ... // see spray documentation for more info on routing
}
```

Now we can create and start a server using this service.

``` scala

implicit val system = ActorSystem("my-actor-system")

val server = new Server(
  name = "my-server", // used to name underlying actors
  host = "localhost",
  port = Port.free, // this finds a free port, it's just an Int
  serviceFactory = Props(MyService))

// start returns a Future[Server.Stopped]
val stopped = server.start()

// shutdown the actor system when the server stops
stopped.onComplete { _ =>
  system.shutdown()
}

```

Stopping the server
-------------------

This can be done in three ways:

1. Call `server.stop()`
2. Dispatch a `Server.Stop` message from the service (`context.parent ! Server.Stop`)
3. Automatically when there is no more activity: `new Server(..., idleTimeout = 1.second)`

Examples
--------

See the examples in the `examples` directory