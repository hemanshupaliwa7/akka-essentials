package part4faulttolerance.receptionistexamples

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior,ActorContext,Behaviors}
import akka.actor.typed.receptionist.{Receptionist,ServiceKey}

object BrainMessageAdaptar {

  sealed trait MessageToBrain
  final case object FindTheMouth extends MessageToBrain
  private case class ListingResponse(listing: Receptionist.Listing)
    extends MessageToBrain

  // this line of code is long, so i wrapped it onto two lines
  def apply(): Behavior[MessageToBrain] = Behaviors.setup {
    context: ActorContext[MessageToBrain] =>

      // (1) we can’t initially get a reference to the Mouth actor, so
      // declare this variable as a var field, and using Option/None
      var mouth: Option[ActorRef[Mouth.MessageToMouth]] = None

      // (2) create an ActorRef that can be thought of as a Receptionist
      // Listing “adapter.” this will be used in the next line of code.
      // the Brain.ListingResponse(listing) part of the code tells the
      // Receptionist how to get back in touch with us after we contact
      // it in Step 4 below.
      // also, this line of code is long, so i wrapped it onto two lines.
      val listingAdapter: ActorRef[Receptionist.Listing] =
      context.messageAdapter { listing =>
        println(s"listingAdapter:listing: ${listing.toString}")
        BrainMessageAdaptar.ListingResponse(listing)
      }

      // (3) send a message to the Receptionist saying that we want
      // to subscribe to events related to Mouth.MouthKey, which
      // represents the Mouth actor.
      context.system.receptionist !
        Receptionist.Subscribe(Mouth.MouthKey, listingAdapter)

      Behaviors.receiveMessage { message =>
        message match {
          case FindTheMouth =>
            // (4) send a Find message to the Receptionist, saying
            // that we want to find any/all listings related to
            // Mouth.MouthKey, i.e., the Mouth actor.
            println(s"Brain: got a FindTheMouth message")
            context.system.receptionist !
              Receptionist.Find(Mouth.MouthKey, listingAdapter)
            Behaviors.same
          case ListingResponse(Mouth.MouthKey.Listing(listings)) =>
            // (5) after Step 4, the Receptionist sends us this
            // ListingResponse message. the `listings` variable is
            // a Set of ActorRef of type Mouth.MessageToMouth, which
            // you can interpret as “a set of Mouth ActorRefs.” for
            // this example i know that there will be at most one
            // Mouth actor, but in other cases there may be more
            // than one actor in this set.
            println(s"Brain [${context.self.path}]: got a ListingResponse message")
            // i add this line just to be clear about `listings` type
            val xs: Set[ActorRef[Mouth.MessageToMouth]] = listings
            // loop through all of the ActorRefs
            for (x <- xs) {
              // there should be only one ActorRef, so i assign it
              // to the `mouth` variable i created earlier
              mouth = Some(x)
              // send a SpeakText message to the Mouth actor
              mouth.foreach{ m =>
                m ! Mouth.SpeakText("Brain says hello to Mouth")
              }
            }
            Behaviors.same
        }
      }
  }
}