package part4faulttolerance

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object StartingStoppingActors1 extends App {

  object Main {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      import Parent._
      val parent = ctx.spawn(Parent(), "parent")
      parent ! SpawnJob("child")
      parent ! GracefulShutdown

      Behaviors.empty
    }
  }
  ActorSystem[Nothing](Main(), "StoppingActorsDemo")

  object Parent {
    sealed trait Command
    final case class SpawnJob(name: String) extends Command
    case object GracefulShutdown extends Command

    def apply(): Behavior[Command] = {
      Behaviors
        .receive[Command] { (context, message) =>
          message match {
            case SpawnJob(jobName) =>
              context.log.info("Spawning job {}!", jobName)
              context.spawn(Child(jobName), name = jobName)
              Behaviors.same
            case GracefulShutdown =>
              context.log.info("Initiating graceful shutdown...")
              // Here it can perform graceful stop (possibly asynchronous) and when completed
              // return `Behaviors.stopped` here or after receiving another message.
              Behaviors.stopped
          }
        }
        .receiveSignal {
          case (context, PostStop) =>
            context.log.info("Master Control Program stopped")
            Behaviors.same
        }
    }
  }

  object Child {
    sealed trait Command

    def apply(name: String): Behavior[Command] = {
      Behaviors.receiveSignal[Command] {
        case (context, PostStop) =>
          context.log.info("Worker {} stopped", name)
          Behaviors.same
      }
    }
  }

}
