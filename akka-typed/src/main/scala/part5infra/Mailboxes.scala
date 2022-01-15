package part5infra

import akka.{actor => classicActor}
import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector, MailboxSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {

  object SimpleActor {
    def apply(): Behavior[Any] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case message => ctx.log.info(message.toString)
        Behaviors.same
      }
    }
  }


  /**
   * Interesting case #1 - custom priority mailbox
   * P0 -> most important
   * P1
   * P2
   * P3
   */

  // step 1 - mailbox definition
  class SupportTicketPriorityMailbox(settings: classicActor.ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox(
      PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _ => 4
      })

  // step 2 - make it known in the config
  // step 3 - attach the dispatcher to an actor

  val root = Behaviors.setup[Nothing] { ctx =>

    val supportTicketLogger = ctx.spawn(SimpleActor(), "simpleActor", MailboxSelector.fromConfig("support-ticket-dispatcher"))
    supportTicketLogger ! "[P3] this thing would be nice to have"
    supportTicketLogger ! "[P0] this needs to be solved NOW!"
    supportTicketLogger ! "[P1] do this when you have the time"


    /**
     * Interesting case #2 - control-aware mailbox
     * we'll use UnboundedControlAwareMailbox
     */
    // step 1 - mark important messages as control messages
    case object ManagementTicket extends ControlMessage

    /*
      step 2 - configure who gets the mailbox
      - make the actor attach to the mailbox
     */
    // method #1
    val controlAwareActor = ctx.spawn(SimpleActor(), "simpleActorControl", MailboxSelector.fromConfig("control-mailbox"))
//    controlAwareActor ! "[P0] this needs to be solved NOW!"
//    controlAwareActor ! "[P1] do this when you have the time"
//    controlAwareActor ! ManagementTicket

    // method #2 - using deployment config
    val altControlAwareActor = ctx.spawn(SimpleActor(), "altControlAwareActor")
    altControlAwareActor ! "[P0] this needs to be solved NOW!"
    altControlAwareActor ! "[P1] do this when you have the time"
    altControlAwareActor ! ManagementTicket

    Behaviors.empty
  }
  ActorSystem[Nothing](root, "MailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

}
