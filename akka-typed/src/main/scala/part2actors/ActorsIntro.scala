package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ActorsIntro extends App {

  object RootBehaviour {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      // part3 - instantiate our actor
      val wordCounter = ctx.spawn(WordCountActor(), "wordCounter")
      val anotherWordCounter = ctx.spawn(WordCountActor(), "anotherWordCounter")

      // part4 - communicate!
      wordCounter ! "I am learning Akka and it's pretty damn cool!" // "tell"
      anotherWordCounter ! "A different message"
      // asynchronous!

      Behaviors.empty
    }
  }
  // part1 - actor systems
  val actorSystem = ActorSystem[Nothing](RootBehaviour(), "firstActorSystem")
  println(actorSystem.name)

  // part2 - create actors
  // word count actor
  object WordCountActor {
    // internal data
    var totalWords = 0

    // behavior
    def apply(): Behavior[Any] = Behaviors.setup[Any] { ctx =>
      Behaviors.receiveMessage {
        case message: String =>
          println(s"[word counter] I have received: $message")
          totalWords += message.split(" ").length
          Behaviors.same
        case msg => println(s"[word counter] I cannot understand ${msg.toString}")
          Behaviors.same
      }
    }
  }

}
