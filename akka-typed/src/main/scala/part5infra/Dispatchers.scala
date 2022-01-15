package part5infra

import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Dispatchers extends App {

  object Counter {
    var count = 0

    def apply(): Behavior[Any] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case message: Any =>
          count += 1
          ctx.log.info(s"[$count] $message")
          Behaviors.same
      }
    }
  }

  /**
   * Dispatchers implement the ExecutionContext trait
   */
  object DBActor {

    def apply(): Behavior[Any] =
      Behaviors.receive { (context , message) =>
        implicit val executionContext: ExecutionContext = context.system.dispatchers
          .lookup(DispatcherSelector.fromConfig("my-dispatcher"))
        message match {
          case m => Future {
            context.log.info(s"success: $m")
          }
          Behaviors.same
        }
    }
  }

  val root = Behaviors.setup[Nothing] { ctx =>

    // method #1 - programmatic/in code
    val actors = for (i <- 1 to 10) yield ctx.spawn(Counter(), s"counter_$i", DispatcherSelector.fromConfig("my-dispatcher"))

//    val r = new Random()
//    for (i <- 1 to 1000) {
//      actors(r.nextInt(10)) ! i
//    }
    val rtjvmActor = ctx.spawn(Counter(), "rtjvm")
//    rtjvmActor ! 10

    val dbActor = ctx.spawn(DBActor(), "dbActor")
//    dbActor ! "the meaning of life is 42"
    val nonblockingActor = ctx.spawn(Counter(), "nonblockingActor")

    for (i <- 1 to 1000) {
      val message = s"important message $i"
      dbActor ! message
      nonblockingActor ! message
    }

    Behaviors.empty
  }
  // method #2 from config
  ActorSystem[Nothing](root, "DispatcherDemo"/*,DispatcherSelector.fromConfig("my-dispatcher")*/)
}
