package part2actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

object ChangingActorBehavior extends App {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      val fussyKid = ctx.spawn(FussyKid(), "fussyKid")
      val statelessFussyKid = ctx.spawn(StatelessFussyKid(), "statelessFussyKid")
      val mom = ctx.spawn(Mom(), "mom")

      mom ! Mom.MomStart(fussyKid)
      mom ! Mom.MomStart(statelessFussyKid)

      // Exercise 1
      import Counter._
      val counter = ctx.spawn(Counter(), "myCounter")

      (1 to 5).foreach(_ => counter ! Increment)
      (1 to 3).foreach(_ => counter ! Decrement)
      counter ! Print

      // Exercise 2
      import Citizen._
      import VoteAggregator._
      val alice = ctx.spawn(Citizen(), "alice")
      val bob = ctx.spawn(Citizen(), "bob")
      val charlie = ctx.spawn(Citizen(), "charlie")
      val daniel = ctx.spawn(Citizen(), "daniel")

      alice ! Vote("Martin")
      bob ! Vote("Jonas")
      charlie ! Vote("Roland")
      daniel ! Vote("Roland")

      val voteAggregator = ctx.spawn(VoteAggregator(), "voteAggregator")
      voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))

      /*
        Print the status of the votes

        Martin -> 1
        Jonas -> 1
        Roland -> 2
       */

      Behaviors.empty
    }
  }
  ActorSystem[Nothing](RootBehavior(), "changingActorBehaviorDemo")

  object FussyKid {
    import Mom._

    sealed trait Response
    case object KidAccept extends Response
    case object KidReject extends Response
    val HAPPY = "happy"
    val SAD = "sad"

    // internal state of the kid
    var state = HAPPY

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Food(VEGETABLE) => state = SAD
          Behaviors.same
        case Food(CHOCOLATE) => state = HAPPY
          Behaviors.same
        case Ask(_, ref) =>
          if (state == HAPPY) ref ! KidAccept
          else ref ! KidReject
          Behaviors.same
      }
    }
  }

  object StatelessFussyKid {

    import FussyKid._
    import Mom._

    def apply(): Behavior[Command] = kidsMood(HAPPY)

    private def kidsMood(state: String): Behavior[Command] = Behaviors.receiveMessage {
      case Food(VEGETABLE) =>
        kidsMood(SAD)
      case Food(CHOCOLATE) =>
        kidsMood(HAPPY)
      case Ask(_, ref) =>
        if (state == HAPPY) ref ! KidAccept
        else ref ! KidReject
        Behaviors.empty
    }
  }

  object Mom {
    import FussyKid._
    case class MomStart(kidRef: ActorRef[Command]) extends Response

    sealed trait Command
    case class Food(food: String) extends Command
    case class Ask(message: String, kidRef: ActorRef[FussyKid.Response]) extends Command// do you want to play?
    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"

    def apply(): Behavior[Response] = Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case MomStart(kidRef) =>
          // test our interaction
          kidRef ! Food(VEGETABLE)
          kidRef ! Food(VEGETABLE)
          kidRef ! Food(CHOCOLATE)
          kidRef ! Food(CHOCOLATE)
          kidRef ! Ask("do you want to play?", ctx.self)
          Behaviors.same
        case KidAccept => println("Yay, my kid is happy!")
          Behaviors.same
        case KidReject => println("My kid is sad, but as he's healthy!")
          Behaviors.same
      }
    }
  }

  /*
    mom receives MomStart
      kid receives Food(veg) -> kid will change the handler to sadReceive
      kid receives Ask(play?) -> kid replies with the sadReceive handler =>
    mom receives KidReject
   */


  /*

  context.become

    Food(veg) -> stack.push(sadReceive)
    Food(chocolate) -> stack.push(happyReceive)

    Stack:
    1. happyReceive
    2. sadReceive
    3. happyReceive
   */

  /*
    new behavior
    Food(veg)
    Food(veg)
    Food(choco)
    Food(choco)

    Stack:

    1. happyReceive
   */

  /**
   * Exercises
   * 1 - recreate the Counter Actor with context.become and NO MUTABLE STATE
   */
  object Counter {
    sealed trait Command
    case object Increment extends Command
    case object Decrement extends Command
    case object Print extends Command

    def apply(): Behavior[Command] = countReceive(0)

    private def countReceive(currentCount: Int): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case Increment =>
          println(s"[countReceive($currentCount)] incrementing")
          countReceive(currentCount + 1)
        case Decrement =>
          println(s"[countReceive($currentCount)] decrementing")
          countReceive(currentCount - 1)
        case Print =>
          println(s"[countReceive($currentCount)] my current count is $currentCount")
          Behaviors.empty
      }
    }
  }


  /**
   * Exercise 2 - a simplified voting system
   */
  object Citizen {
    sealed trait Command
    case class Vote(candidate: String) extends Command
    case class VoteStatusRequest(actorRef: ActorRef[VoteAggregator.VoteStatusReply]) extends Command

    def apply(): Behavior[Command] = Behaviors.receive {(ctx, msg) =>
      msg match {
        case Vote(c) => voted(c)
        case VoteStatusRequest(actorRef) =>
          actorRef ! VoteAggregator.VoteStatusReply(None, ctx.self)
          Behaviors.same
      }
    }
    private def voted(candidate: String): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case VoteStatusRequest(actorRef) =>
          actorRef ! VoteAggregator.VoteStatusReply(Some(candidate), ctx.self)
          Behaviors.same
      }
    }
  }

  object VoteAggregator {
    sealed trait Command
    case class AggregateVotes(citizens: Set[ActorRef[Citizen.VoteStatusRequest]]) extends Command
    case class VoteStatusReply(candidate: Option[String], actorRef: ActorRef[Citizen.VoteStatusRequest]) extends Command

    def apply(): Behavior[Command] = awaitingCommand

    private def awaitingCommand: Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case AggregateVotes(citizens) =>
          citizens.foreach(citizenRef => citizenRef ! Citizen.VoteStatusRequest(ctx.self))
          awaitingStatuses(citizens, Map())
      }
    }
    private def awaitingStatuses(stillWaiting: Set[ActorRef[Citizen.VoteStatusRequest]], currentStats: Map[String, Int]): Behavior[Command] =
      Behaviors.receive {(ctx, msg) =>
        msg match {
          case VoteStatusReply(None, actorRef) =>
            // a citizen hasn't voted yet
            actorRef ! Citizen.VoteStatusRequest(ctx.self)  // this might end up in an infinite loop
            Behaviors.same
          case VoteStatusReply(Some(candidate), actorRef) =>
            val newStillWaiting = stillWaiting - actorRef
            val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
            val newStats = currentStats + (candidate -> (currentVotesOfCandidate + 1))
            if (newStillWaiting.isEmpty) {
              println(s"[aggregator] poll stats: $newStats")
              Behaviors.empty
            } else {
              // still need to process some statuses
              awaitingStatuses(newStillWaiting, newStats)
            }
        }
      }
  }

}
