package part4faulttolerance

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors

import java.util.concurrent.ThreadLocalRandom
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

object SupervisionSpec extends App {

  object Worker {
    sealed trait Command
    final case class Job(payload: String) extends Command
    val workerBehavior: Behavior[Command] =
      active(count = 1)

    private def active(count: Int): Behavior[Command] =
      Behaviors.receive[Command] { (ctx, msg) =>
        msg match {
          case Job(payload) =>
            if (ThreadLocalRandom.current().nextInt(5) == 0)
              throw new RuntimeException("Bad luck")

            ctx.system.log.info(s"Worker ${ctx.self} got job $payload, count $count")
            active(count + 1)
        }
      }
  }

  val root: Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    import Worker._

    val strategy = SupervisorStrategy.restart
//    val worker = ctx.spawn(Behaviors.supervise(workerBehavior).onFailure[RuntimeException](strategy), "worker")
//    val worker = ctx.spawn(Behaviors.supervise(workerBehavior).onFailure[RuntimeException](DemonstrateOtherStrategies.strategy2), "worker")
//    val worker = ctx.spawn(Behaviors.supervise(workerBehavior).onFailure[RuntimeException](DemonstrateOtherStrategies.strategy3), "worker")
//    val worker = ctx.spawn(Behaviors.supervise(workerBehavior).onFailure[RuntimeException](DemonstrateOtherStrategies.strategy4), "worker")
    val worker = ctx.spawn(Behaviors.supervise(workerBehavior).onFailure[RuntimeException](DemonstrateOtherStrategies.backoff), "worker")
//    val worker = ctx.spawn(DemonstrateNesting.behv, "worker")

    (1 to 20).foreach { n =>
      worker ! Job(n.toString)
    }

    Behaviors.empty
  }
  val system: ActorSystem[Nothing] = ActorSystem[Nothing](root, "Sys")
  try {
    // Exit the system after ENTER is pressed
    StdIn.readLine()
  } finally {
    system.terminate()
  }

  object DemonstrateOtherStrategies {
    val strategy2 = SupervisorStrategy.restart.withLoggingEnabled(false)
    val strategy3 = SupervisorStrategy.resume
    val strategy4 = SupervisorStrategy.restart.withLimit(maxNrOfRetries = 3, 1.second)

    val backoff = SupervisorStrategy.restartWithBackoff(
      minBackoff = 200.millis, maxBackoff = 10.seconds, randomFactor = 0.1)
  }

  object DemonstrateNesting {
    import Worker._
    import SupervisorStrategy._
    val behv: Behavior[Command] =
      Behaviors.supervise(
        Behaviors.supervise(workerBehavior).onFailure[IllegalStateException](restart.withLimit(maxNrOfRetries = 3, 1.second)))
        .onFailure[RuntimeException](restart)
  }

}