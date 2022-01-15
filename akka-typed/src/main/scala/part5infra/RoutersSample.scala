package part5infra

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, Routers}

import scala.concurrent.duration._
import scala.io.StdIn

object RoutersSample extends App {

  object Routee {

    val PingServiceKey: ServiceKey[Command] = ServiceKey[Command]("pingService")

    sealed trait Command
    final case class Ping(replyTo: ActorRef[Pong.type]) extends Command
    final case object Pong extends Command

    val behavior: Behavior[Command] =
      Behaviors.setup { ctx ⇒
        ctx.system.receptionist ! Receptionist.Register(PingServiceKey, ctx.self, ctx.system.deadLetters)

        Behaviors.receive { (ctx, msg) ⇒
          msg match {
            case Ping(replyTo) ⇒
              println(s"Routee ${ctx.self} got ping")
              Behaviors.same
          }
        }
      }
  }

  object RouterBot {

    sealed trait BotMessage
    private case object Tick extends BotMessage
    private case object GotPong extends BotMessage

    val behavior: Behavior[BotMessage] =
      Behaviors.setup[BotMessage] { ctx ⇒
        // Group Router
        val router = ctx.spawn(Routers.group(Routee.PingServiceKey), "pingRouter")
        // Pool Router
        val router1 = ctx.spawn(Routers.pool(4){
          Behaviors.supervise(Routee.behavior).onFailure[Exception](SupervisorStrategy.restart)
        }, "pingRouter1")
        val pongAdapter: ActorRef[Routee.Pong.type] = ctx.spawnAnonymous[Routee.Pong.type](Behaviors.empty)

        Behaviors.withTimers { timers ⇒

          timers.startTimerWithFixedDelay(Tick, Tick, 1.second)

          Behaviors.receive[BotMessage] { (ctx, msg) ⇒
            msg match {
              case Tick ⇒
                println(s"Bot ${ctx.self} sending ping")
                router ! Routee.Ping(pongAdapter)
//                router1 ! Routee.Ping(pongAdapter)
                Behaviors.same
              case GotPong ⇒
                println(s"Bot ${ctx.self} got pong")
                Behaviors.same
            }
          }
        }
      }
  }

  val root = Behaviors.setup[Nothing] { ctx =>

    ctx.spawn(Routee.behavior, "routee1")
    ctx.spawn(Routee.behavior, "routee2")
    ctx.spawn(Routee.behavior, "routee3")

    ctx.spawn(RouterBot.behavior, "bot")
    Behaviors.empty
  }
  val system = ActorSystem[Nothing](root, "Sys")
  try {
    // Exit the system after ENTER is pressed
    StdIn.readLine()
  } finally {
    system.terminate()
  }

}
