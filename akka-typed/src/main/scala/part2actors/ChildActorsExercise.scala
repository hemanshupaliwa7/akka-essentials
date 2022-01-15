package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ChildActorsExercise extends App {

  // Distributed Word counting
  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      val testActor = ctx.spawn(TestActor(), "testActor")
      testActor ! TestActor.Go
      Behaviors.empty
    }
  }
  ActorSystem[Nothing](RootBehavior(), "roundRobinWordCountExercise")

  // Create Master Actor
  object WordCounterMaster {
    sealed trait Command
    case class Initialize(nChildren: Int) extends Command
    case class RequestReceived(text: String, actorRef: ActorRef[TestActor.Response]) extends Command
    case class WordCountTask(id: Int, text: String, actorRef: ActorRef[WordCountReply]) extends Command
    case class WordCountReply(id: Int, count: Int) extends Command

    def apply(): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case Initialize(nChildren) =>
          println("[master] initializing...")
          val childrenRefs = for (i <- 1 to nChildren) yield ctx.spawn(WordCounterWorker(), s"wcw_$i")
          withChildren(childrenRefs, 0, 0, Map())
      }
    }

    private def withChildren(childrenRefs: Seq[ActorRef[WordCountTask]], currentChildIndex: Int, currentTaskId: Int, requestMap: Map[Int, ActorRef[TestActor.Response]]): Behavior[Command] =
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case RequestReceived(text, actorRef) =>
            println(s"[master] I have received: $text - I will send it to child $currentChildIndex")
            val originalSender = actorRef
            val task = WordCountTask(currentTaskId, text, ctx.self)
            val childRef = childrenRefs(currentChildIndex)
            childRef ! task
            val nextChildIndex = (currentChildIndex + 1) % childrenRefs.length
            val newTaskId = currentTaskId + 1
            val newRequestMap = requestMap + (currentTaskId -> originalSender)
            withChildren(childrenRefs, nextChildIndex, newTaskId, newRequestMap)
          case WordCountReply(id, count) =>
            println(s"[master] I have received a reply for task id $id with $count")
            val originalSender = requestMap(id)
            originalSender ! TestActor.Response(count)
            withChildren(childrenRefs, currentChildIndex, currentTaskId, requestMap - id)
        }
      }
  }

  // Create Worker Actor
  object WordCounterWorker {
    def apply(): Behavior[WordCounterMaster.WordCountTask] = Behaviors.receive { (ctx, msg) =>
      println(s"${ctx.self.path} I have received task ${msg.id} with ${msg.text}")
      msg.actorRef ! WordCounterMaster.WordCountReply(msg.id, msg.text.split(" ").length)
      Behaviors.same
    }
  }

  // Test Actor that triggers process
  object TestActor {
    sealed trait Command
    case object Go extends Command
    case class Response(count: Int) extends Command

    def apply(): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case Go =>
          val master = ctx.spawn(WordCounterMaster(), "master")
          master ! WordCounterMaster.Initialize(3)
          val texts = List("I love Akka", "Scala is super dope", "yes", "me too")
          texts.foreach(text => master ! WordCounterMaster.RequestReceived(text, ctx.self))
          Behaviors.same
        case Response(count) =>
          println(s"[test actor] I received a reply: $count")
          Behaviors.same
      }
    }
  }


}
