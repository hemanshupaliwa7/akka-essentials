package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ChildActors extends App {

  object RootBehaviour {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      val parent = ctx.spawn(Parent(), "parent")
      parent ! Parent.CreateChild("child")
      parent ! Parent.TellChild("hey Kid!")

      Behaviors.empty
    }
  }
  ActorSystem[Nothing](RootBehaviour(), "ParentChildDemo")

  object Parent {
    sealed trait Command
    case class CreateChild(name: String) extends Command
    case class TellChild(message: String) extends Command

    case class Response(message: Any)
    def apply(): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case CreateChild(name) =>
          println(s"${ctx.self.path} creating child")
          // create a new actor right HERE
          val childRef = ctx.spawn(Child(), name)
          withChild(childRef)
      }
    }
    private def withChild(childRef: ActorRef[Response]): Behavior[Command] = Behaviors.receiveMessage {
      case TellChild(message) => childRef ! Response(message)
        Behaviors.same
    }
  }

  object Child {
    def apply(): Behavior[Parent.Response] = Behaviors.receive { (ctx, msg) =>
      println(s"${ctx.self.path} I got: $msg")
      Behaviors.same
    }
  }


}
