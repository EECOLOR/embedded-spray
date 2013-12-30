Embedded Spray server
=====================

A small wrapper around Spray's http and routing framework.

Installation
------------

``` scala
libraryDependencies += "org.qirx" %% "embedded-spray" % "0.1"

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
  idleTimeout = 2.seconds // automatically unbind if there is no activity
)

val listener = server.bind(
  host = "localhost",
  port = Port.free, // this finds a free port, it's just an Int
  serviceFactory = Props(MyService))

listener.bound.onComplete { _ =>
  // the service is bound to the host and port

  // if you want you can unbind the service
  listener.unbind()
}

listener.unbound.onComplete { _ =>
  // there service has been unbound
}

// timeout is for the individual processes that need to be stopped
val closed = server.close(2.seconds)

// you can now safely shutdown the actor system
closed.onComplete { _ =>
  system.shutdown()
}

```

Unbinding a service
-------------------

This can be done in three ways:

1. Call `listener.unbind()`
2. Dispatch a `Listener.Unbind` message from the service (`context.parent ! Listener.Unbind`)
3. Automatically when there is no more activity: `new Server(..., idleTimeout = 1.second)`

Examples
--------

See the examples in the `examples` directory