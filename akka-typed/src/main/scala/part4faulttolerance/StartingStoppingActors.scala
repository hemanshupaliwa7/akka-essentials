package part4faulttolerance

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object StartingStoppingActors extends App {

  object Main {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      import Parent._
      val parent = ctx.spawn(Parent(), "parent")
      parent ! StartChild("child")
      parent ! StopChild("child")

      Behaviors.empty
    }
  }
  ActorSystem[Nothing](Main(), "StoppingActorsDemo")

  object Parent {
    sealed trait Command
    case class StartChild(name: String) extends Command
    case class StopChild(name: String) extends Command
    case object Stop extends Command

    def apply(): Behavior[Command] = withChildren(Map())

    def withChildren(children: Map[String, ActorRef[Any]]): Behavior[Command] = Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case StartChild(name) =>
          ctx.log.info(s"Starting child $name")
          withChildren(children + (name -> ctx.spawn(Child(), name)))
        case StopChild(name) =>
          ctx.log.info(s"Stopping child with the name $name")
          val childOption = children.get(name)
          childOption.foreach(childRef => ctx.stop(childRef))
          Behaviors.same
        case Stop =>
          ctx.log.info("Stopping myself")
          ctx.stop(ctx.self)
          Behaviors.stopped
        case message =>
          ctx.log.info(message.toString)
          Behaviors.same
      }
    }.receiveSignal {
      case (context, PostStop) =>
        context.log.info("Master Control Program stopped")
        Behaviors.same
    }
  }

  object Child {
    def apply(): Behavior[Any] = Behaviors.receive[Any] { (ctx, msg) =>
      ctx.log.info(msg.toString)
      Behaviors.same
    }
  }

}
