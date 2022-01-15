package part3testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike
import part3testing.BasicSpec.SimpleActor.Request

import scala.concurrent.duration._
import scala.util.Random

class BasicSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import BasicSpec._

  "A simple actor" should {
    "send back the same message" in {
      val echoActor = testKit.spawn(SimpleActor())
      val probe = testKit.createTestProbe[Any]()
      val message = "hello, test"
      echoActor ! Request(message, probe.ref)

      probe.expectMessage(Request(message, probe.ref)) // akka.test.single-expect-default
    }
  }

  "A blackhole actor" should {
    "send back some message" in {
      val blackhole = testKit.spawn(Blackhole())
      val probe = testKit.createTestProbe[Any]()
      val message = "hello, test"
      blackhole ! message

      probe.expectNoMessage(1 second)
    }
  }

  // message assertions
  "A lab test actor" should {
    val labTestActor = testKit.spawn(LabTestActor())
    val probe = testKit.createTestProbe[String]()

    "turn a string into uppercase" in {
      labTestActor ! LabTestActor.Request("I love Akka", probe.ref)
      val reply = probe.expectMessageType[String]

      assert(reply == "I LOVE AKKA")
    }

    "reply to a greeting" in {
      labTestActor ! LabTestActor.Request("greeting", probe.ref)
      val reply = probe.expectMessageType[String]

      assert(reply == "hi" || reply == "hello")
    }

    "reply with favorite tech" in {
      labTestActor ! LabTestActor.Request("favoriteTech", probe.ref)
      val reply = probe.expectMessageType[String]

      assert(reply == "Scala" || reply == "Akka")
    }

    "reply with cool tech in a different way" in {
      labTestActor ! LabTestActor.Request("favoriteTech", probe.ref)
      val messages = probe.receiveMessages(2) // Seq[Any]

      // free to do more complicated assertions
    }
  }

}

object BasicSpec {
  object SimpleActor {
    case class Request(message: Any, actorRef: ActorRef[Any])

    def apply(): Behavior[Request] = Behaviors.receiveMessage {
      message => message.actorRef ! message
      Behaviors.same
    }
  }

  object Blackhole {
    def apply(): Behavior[Any] = Behaviors.empty
  }

  object LabTestActor {
    case class Request(message: String, actorRef: ActorRef[String])

    val random = new Random()
    def apply(): Behavior[Request] = Behaviors.receiveMessage {
      case Request(message, actorRef) if message == "greeting" =>
        if (random.nextBoolean()) actorRef ! "hi" else actorRef ! "hello"
        Behaviors.same
      case Request(message, actorRef) if message == "favoriteTech" =>
        actorRef ! "Scala"
        actorRef ! "Akka"
        Behaviors.same
      case Request(message, actorRef) =>
        actorRef  ! message.toUpperCase()
        Behaviors.same
    }
  }
}