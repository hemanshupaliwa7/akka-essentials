package part4faulttolerance

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, PreRestart, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import java.io.{FileWriter, PrintWriter}
import java.util.concurrent.ThreadLocalRandom
import scala.concurrent.duration._
import scala.io.StdIn

object SupervisionSpec2 extends App {
  object Worker {

    sealed trait Command

    final case class Job(payload: String) extends Command

    val workerBehavior: Behavior[Command] = Behaviors.setup { ctx =>
      ctx.system.log.info(s"Worker ${ctx.self} is STARTED")
      val out = new PrintWriter(new FileWriter(
        s"target/out-${ctx.self.path.name}.txt", true))
      active(count = 1, out)
    }

    private def active(count: Int, out: PrintWriter): Behavior[Command] =
      Behaviors.receive[Command] { (ctx, msg) =>
        msg match {
          case Job(payload) =>
            if (ThreadLocalRandom.current().nextInt(5) == 0)
              throw new RuntimeException("Bad luck")

            ctx.system.log.info(s"Worker ${ctx.self} got job $payload, count $count")
            out.println(s"Worker ${ctx.self} got job $payload, count $count")
            active(count + 1, out)
        }
      }.receiveSignal {
        case (ctx, PreRestart) =>
          ctx.system.log.info(s"Worker ${ctx.self} is RESTARTED, count $count")
          out.close()
          Behaviors.same
        case (ctx, PostStop) =>
          ctx.system.log.info(s"Worker ${ctx.self} is STOPPED, count $count")
          out.close()
          Behaviors.same
      }
  }

  object WorkerManager {

    sealed trait Command

    final case class Job(partition: Int, payload: String) extends Command

    private final case class WorkerStopped(partition: Int) extends Command

    private val strategy = SupervisorStrategy.restart.withLimit(maxNrOfRetries = 2, 1.second)
    private val worker: Behavior[Worker.Command] =
      Behaviors.supervise(Worker.workerBehavior).onFailure[RuntimeException](strategy)

    val workerManagerBehavior: Behavior[Command] =
      activeOption2(Map.empty)

    private def spawnWorker(partition: Int, ctx: ActorContext[Command]): ActorRef[Worker.Command] = {
      val w = ctx.spawn(worker, s"worker-$partition")
      ctx.watchWith(w, WorkerStopped(partition))
      w
    }

    // illustrate ordinary watch + Terminated
    private def activeOption1(workers: Map[Int, ActorRef[Worker.Command]]): Behavior[Command] = {
      Behaviors.receive[Command] { (ctx, msg) =>
        msg match {
          case job@Job(partition, payload) =>
            val (w, newWorkers) = workers.get(partition) match {
              case Some(w) =>
                (w, workers)
              case None =>
                val w = spawnWorker(partition, ctx)
                (w, workers.updated(partition, w))
            }
            w ! Worker.Job(payload)
            activeOption1(newWorkers)

          case _: WorkerStopped => Behaviors.same // not used in this option, but silence compiler warning
        }
      }.receiveSignal {
        case (ctx, Terminated(ref)) =>
          ctx.system.log.info(s"Worker $ref is TERMINATED")
          val newWorkers = workers.filterNot { case (_, w) => w == ref }
          activeOption1(newWorkers)
      }
    }

    // illustrate watchWith
    private def activeOption2(workers: Map[Int, ActorRef[Worker.Command]]): Behavior[Command] = {
      Behaviors.receive[Command] { (ctx, msg) =>
        msg match {
          case job@Job(partition, payload) =>
            val (w, newWorkers) = workers.get(partition) match {
              case Some(w) =>
                (w, workers)
              case None =>
                val w = spawnWorker(partition, ctx)
                (w, workers.updated(partition, w))
            }
            w ! Worker.Job(payload)
            activeOption2(newWorkers)

          case WorkerStopped(partition) =>
            ctx.system.log.info(s"Worker ${workers(partition)} is TERMINATED")
            activeOption2(workers - partition)
        }
      }
    }

  }

  val root = Behaviors.setup[Nothing] { ctx =>

    val manager = ctx.spawn(WorkerManager.workerManagerBehavior, "workerManager")

    val numberOfPartitions = 3
    (1 to 30).foreach { n =>
      val partition = n % numberOfPartitions
      manager ! WorkerManager.Job(partition, n.toString)
    }

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