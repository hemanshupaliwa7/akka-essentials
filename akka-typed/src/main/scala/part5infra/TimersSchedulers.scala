package part5infra

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.duration._
import scala.io.StdIn

object TimersSchedulers extends App {

  object Buncher {
    trait Msg
    final case class Batch(messages: Vector[Msg])

    private case object TimerKey
    private case object Timeout extends Msg

    def behavior(target: ActorRef[Batch], after: FiniteDuration, maxSize: Int): Behavior[Msg] =
      Behaviors.withTimers(timers => idle(timers, target, after, maxSize))

    private def idle(timers: TimerScheduler[Msg], target: ActorRef[Batch],
                     after: FiniteDuration, maxSize: Int): Behavior[Msg] = {
      Behaviors.receive[Msg] { (ctx, msg) =>
        timers.startSingleTimer(TimerKey, Timeout, after)
        active(Vector(msg), timers, target, after, maxSize)
      }
    }

    private def active(buffer: Vector[Msg], timers: TimerScheduler[Msg],
                       target: ActorRef[Batch], after: FiniteDuration, maxSize: Int): Behavior[Msg] = {
      Behaviors.receive[Msg] { (ctx, msg) =>
        msg match {
          case Timeout =>
            target ! Batch(buffer)
            idle(timers, target, after, maxSize)
          case msg =>
            val newBuffer = buffer :+ msg
            if (newBuffer.size == maxSize) {
              timers.cancel(TimerKey)
              target ! Batch(newBuffer)
              idle(timers, target, after, maxSize)
            } else
              active(newBuffer, timers, target, after, maxSize)
        }
      }
    }
  }

  object BuncherDestination {

    final case class Info(s: String) extends Buncher.Msg

    val behavior: Behavior[Buncher.Batch] =
      Behaviors.receive[Buncher.Batch] { (ctx, batch) =>
        println(s"Got batch of ${batch.messages.size} messages: ${batch.messages.mkString(", ")} ")
        Behaviors.same
      }
  }


  val root = Behaviors.setup[Nothing] { ctx =>
    val destination = ctx.spawn(BuncherDestination.behavior, "destination")
    val buncher = ctx.spawn(
      Buncher.behavior(destination, 3.seconds, 10),
      "buncher")

    (1 to 15).foreach { n =>
      buncher ! BuncherDestination.Info(n.toString)
    }

    ctx.scheduleOnce(1.second, buncher, BuncherDestination.Info("16"))
    ctx.scheduleOnce(2.seconds, buncher, BuncherDestination.Info("17"))

    ctx.scheduleOnce(4.seconds, buncher, BuncherDestination.Info("18"))

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
