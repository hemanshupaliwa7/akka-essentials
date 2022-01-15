package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ActorLoggingDemo extends App {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val simplerActor = ctx.spawn(ActorWithLogging(), "actorWithLogging")
      simplerActor ! "Logging a simple message by extending a trait"

      simplerActor ! (42, 65)
      Behaviors.empty
    }
  }
  ActorSystem[Nothing](RootBehavior(), "LoggingDemo")

  object ActorWithLogging {
    def apply(): Behavior[Any] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case (a,b) =>
          ctx.log.info("Two things: {} and {}", a, b) // Two things: 2 and 3
          Behaviors.same
        case message =>
          ctx.log.info(message.toString)
          Behaviors.same
      }
    }
  }

}
