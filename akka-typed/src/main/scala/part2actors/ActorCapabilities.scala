package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ActorCapabilities extends App {

  object RootBehavior {
    import SimpleActor._
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      val simpleActor = ctx.spawn(SimpleActor(), "simpleActor")
      simpleActor ! "hello, actor"
      // 1 - messages can be of any type
      // a) messages must be IMMUTABLE
      // b) messages must be SERIALIZABLE
      // in practice use case classes and case objects

      simpleActor ! 42 // who is the sender?!
      simpleActor ! SpecialMessage("some special content")

      // 2 - actors have information about their context and about themselves
      // context.self === `this` in OOP
      simpleActor ! SendMessageToYourself("I am an actor and I am proud of it")

      // 3 - actors can REPLY to messages
      val alice = ctx.spawn(SimpleActor(), "alice")
      val bob = ctx.spawn(SimpleActor(), "bob")

      alice ! SayHiTo(bob)

      // 4 - dead letters
      alice ! "Hi!" // reply to "me"

      import Counter._
      val counter = ctx.spawn(Counter(), "myCounter")

      (1 to 5).foreach(_ => counter ! Increment)
      (1 to 3).foreach(_ => counter ! Decrement)
      counter ! Print

      val account = ctx.spawn(BankAccount(), "bankAccount")
      val person = ctx.spawn(Person(), "billionaire")

      import Person._
      person ! LiveTheLife(account)

      Behaviors.empty
    }
  }

  object SimpleActor {
    case class SpecialMessage(contents: String)
    case class SendMessageToYourself(content: String)
    case class SayHiTo(ref: ActorRef[Any])
    case class WirelessPhoneMessage(content: String, ref: ActorRef[_])

    def apply(): Behavior[Any] = Behaviors.setup[Any] { ctx =>
      Behaviors.receiveMessage {
        case message: String => println(s"[${ctx.self}] I have received $message")
          Behaviors.same
        case number: Int => println(s"[simple actor] I have received a NUMBER: $number")
          Behaviors.same
        case SpecialMessage(contents) => println(s"[simple actor] I have received something SPECIAL: $contents")
          Behaviors.same
        case SendMessageToYourself(content) =>
          ctx.self ! content
          Behaviors.same
        case SayHiTo(ref) => ref ! "Hi!" // alice is being passed as the sender
          Behaviors.same
      }
    }
  }

  ActorSystem[Nothing](RootBehavior(), "actorCapabilitiesDemo")


  /**
   * Exercises
   *
   * 1. a Counter actor
   *   - Increment
   *   - Decrement
   *   - Print
   *
   * 2. a Bank account as an actor
   *   receives
   *   - Deposit an amount
   *   - Withdraw an amount
   *   - Statement
   *   replies with
   *   - Success
   *   - Failure
   *
   *   interact with some other kind of actor
   */

  object Counter {
    sealed trait Command
    case object Increment extends Command
    case object Decrement extends Command
    case object Print extends Command

    var count = 0

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Increment => count += 1
          Behaviors.same
        case Decrement => count -= 1
          Behaviors.same
        case Print => println(s"[counter] My current count is $count")
          Behaviors.same
      }
    }
  }

  object BankAccount {
    sealed trait Command
    case class Deposit(amount: Int, actorRef: ActorRef[Person.Response]) extends Command
    case class Withdraw(amount: Int, actorRef: ActorRef[Person.Response]) extends Command
    case class Statement(actorRef: ActorRef[Person.Response]) extends Command

    var funds = 0

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Deposit(amount, actorRef) =>
          if (amount < 0) actorRef ! Person.TransactionFailure("invalid deposit amount")
          else {
            funds += amount
            actorRef ! Person.TransactionSuccess(s"successfully deposited $amount")
          }
          Behaviors.same
        case Withdraw(amount, actorRef) =>
          if (amount < 0) actorRef ! Person.TransactionFailure("invalid withdraw amount")
          else if (amount > funds) actorRef ! Person.TransactionFailure("insufficient funds")
          else {
            funds -= amount
            actorRef ! Person.TransactionSuccess(s"successfully withdrew $amount")
          }
          Behaviors.same
        case Statement(actorRef) => actorRef ! Person.StatementResponse(s"Your balance is $funds")
          Behaviors.same
      }
    }
  }

  object Person {
    sealed trait Response
    case class LiveTheLife(account: ActorRef[BankAccount.Command]) extends Response
    case class TransactionSuccess(message: String) extends Response
    case class TransactionFailure(reason: String) extends Response
    case class StatementResponse(response: String) extends Response

    def apply(): Behavior[Response] = Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case LiveTheLife(account) =>
          account ! BankAccount.Deposit(10000, ctx.self)
          account ! BankAccount.Withdraw(90000, ctx.self)
          account ! BankAccount.Withdraw(500, ctx.self)
          account ! BankAccount.Statement(ctx.self)
          Behaviors.same
        case StatementResponse(response) => println(response)
          Behaviors.same
        case TransactionSuccess(message) => println(message)
          Behaviors.same
        case TransactionFailure(reason) => println(reason)
          Behaviors.same
      }
    }
  }
}
