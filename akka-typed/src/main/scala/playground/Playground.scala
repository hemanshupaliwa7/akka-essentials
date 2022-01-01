package playground

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

/**
  * A simple Scala application. I recommend you fiddle with the code and try your own here.
  * Have fun!
  *
  * Daniel for Rock the JVM
  */
object Playground extends App {

  object RootBehaviour {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val playgroundActor = ctx.spawn(MyPlaygroundActor(), "playgroundActor")
      playgroundActor ! "I love Akka!"
      Behaviors.empty
    }

  }

  object MyPlaygroundActor {
    def apply(): Behavior[Any] = Behaviors.setup { ctx =>
      Behaviors.receiveMessage { message =>
        ctx.log.info(message.toString)
        Behaviors.same
      }
    }
  }
  ActorSystem[Nothing](RootBehaviour(), "Playground")
}
