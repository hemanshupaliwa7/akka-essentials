package part3testing

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpecLike

/*class InterceptingLogsSpec extends ScalaTestWithActorTestKit() with AnyWordSpecLike  {

  import InterceptingLogsSpec._
  val item = "Rock the JVM Akka course"
  val creditCard = "1234-1234-1234-1234"
  val invalidCreditCard = "0000-0000-0000-0000"

  "A checkout flow" should {
    val checkoutRef = testKit.spawn(CheckoutActor())

    "correctly log the dispatch of an order" in {
      LoggingTestKit.info(s"Order [0-9]+ for item $item has been dispatched.").expect {
        // our test code
        checkoutRef ! CheckoutActor.Checkout(item, creditCard)
      }
    }
  }
}*/

object InterceptingLogsSpec extends App {

  object Main {
    val item = "Rock the JVM Akka course"
    val creditCard = "1234-1234-1234-1234"
    val invalidCreditCard = "0000-0000-0000-0000"

    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      val checkoutRef = ctx.spawn(CheckoutActor(), "checkoutActor")
      val checkoutRef2 = ctx.spawn(CheckoutActor(), "checkoutActor2")
      // our test code
      checkoutRef ! CheckoutActor.Checkout(item, invalidCreditCard)
      checkoutRef2 ! CheckoutActor.Checkout(item, creditCard)

      Behaviors.empty
    }
  }
  ActorSystem[Nothing](Main(), "Main")

  object CheckoutActor {
    sealed trait Command
    case class Checkout(item: String, creditCard: String) extends Command
    case object PaymentAccepted extends Command
    case object PaymentDenied extends Command
    case object OrderConfirmed extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val paymentManager = ctx.spawn(PaymentManager(), "paymentManager")
      val fulfillmentManager = ctx.spawn(FulfillmentManager(), "fulfillmentManager")

      awaitingCheckout(paymentManager, fulfillmentManager)
    }

    private def awaitingCheckout(paymentManagerRef: ActorRef[PaymentManager.AuthorizeCard], fulfillmentManagerRef: ActorRef[FulfillmentManager.DispatchOrder]): Behavior[Command] =
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case Checkout(item, card) =>
            paymentManagerRef ! PaymentManager.AuthorizeCard(card, ctx.self)
            pendingPayment(item, paymentManagerRef, fulfillmentManagerRef)
        }
      }

    private def pendingPayment(item: String, paymentManagerRef: ActorRef[PaymentManager.AuthorizeCard], fulfillmentManagerRef: ActorRef[FulfillmentManager.DispatchOrder]): Behavior[Command] =
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case PaymentAccepted =>
            fulfillmentManagerRef ! FulfillmentManager.DispatchOrder(item, ctx.self)
            pendingFulfillment(item, paymentManagerRef, fulfillmentManagerRef)
          case PaymentDenied =>
            throw new RuntimeException("I can't handle this anymore")
        }
      }

    private def pendingFulfillment(item: String, paymentManagerRef: ActorRef[PaymentManager.AuthorizeCard], fulfillmentManagerRef: ActorRef[FulfillmentManager.DispatchOrder]): Behavior[Command] =
      Behaviors.receiveMessage {
        case OrderConfirmed =>
        awaitingCheckout(paymentManagerRef, fulfillmentManagerRef)
      }
  }


  object PaymentManager {
    case class AuthorizeCard(creditCard: String, actorRef: ActorRef[CheckoutActor.Command])

    def apply(): Behavior[AuthorizeCard] = Behaviors.receive { (ctx, msg) =>
      if (msg.creditCard.startsWith("0")) msg.actorRef ! CheckoutActor.PaymentDenied
      else {
//        Thread.sleep(4000)
        msg.actorRef ! CheckoutActor.PaymentAccepted
      }
      Behaviors.same
    }
  }

  object FulfillmentManager {
    case class DispatchOrder(item: String, actorRef: ActorRef[CheckoutActor.Command])

    var orderId = 43
    def apply(): Behavior[DispatchOrder] = Behaviors.receive{ (ctx, msg) =>
      orderId += 1
      ctx.log.info(s"Order $orderId for item ${msg.item} has been dispatched.")
      msg.actorRef ! CheckoutActor.OrderConfirmed
      Behaviors.same
    }
  }
}