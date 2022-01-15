package part3testing

import akka.actor.testkit.typed.scaladsl.{LogCapturing, ManualTime, ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class TimedAssertionsSpec extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike/* with LogCapturing*/ {

  val manualTime: ManualTime = ManualTime()

  "A timer" must {
    "schedule non-repeated ticks" in {
      case object Tick
      case object Tock

      val probe = TestProbe[Tock.type]()
      val behavior = Behaviors.withTimers[Tick.type] { timer =>
        timer.startSingleTimer(Tick, 10.millis)
        Behaviors.receiveMessage { _ =>
          probe.ref ! Tock
          Behaviors.same
        }
      }

      spawn(behavior)

      manualTime.expectNoMessageFor(9.millis, probe)

      manualTime.timePasses(2.millis)
      probe.expectMessage(Tock)

      manualTime.expectNoMessageFor(10.seconds, probe)
    }
  }

}
