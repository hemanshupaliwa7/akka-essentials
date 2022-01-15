package part6patterns

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AskPattern extends App {

  // this code is somewhere else in your app
  object KVActor {
    sealed trait Command
    case class Read(key: String, actorRef: ActorRef[Response]) extends Command
    case class Write(key: String, value: String) extends Command
    case class Response(message: Option[String])

    def apply(): Behavior[Command] = online(Map())

    private def online(kv: Map[String, String]): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case Read(key, actorRef) =>
          ctx.log.info(s"Trying to read the value at the key $key")
          actorRef ! Response(kv.get(key)) // Option[String]
          Behaviors.same
        case Write(key, value) =>
          ctx.log.info(s"Writing the value $value for the key $key")
          online(kv + (key -> value))
      }
    }
  }

  object AuthManager {
    // user authenticator actor
    sealed trait Command
    case class RegisterUser(username: String, password: String) extends Command
    case class Authenticate(username: String, password: String) extends Command
    case class AuthFailure(message: String) extends Command
    case object AuthSuccess extends Command

    val AUTH_FAILURE_NOT_FOUND = "username not found"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "password incorrect"
    val AUTH_FAILURE_SYSTEM = "system error"

    def apply(authDb: ActorRef[KVActor.Command]): Behavior[Command]= Behaviors.receive { (ctx, msg) =>
      // step 2 - logistics
      implicit val timeout: Timeout = Timeout(1.second)
      implicit val executionContext: ExecutionContext = ctx.executionContext
      implicit val scheduler: Scheduler = ctx.system.scheduler

      msg match {
        case RegisterUser(username, password) => authDb ! KVActor.Write(username, password)
          Behaviors.same
        case Authenticate(username, password) => handleAuthentication(username, password, authDb, ctx.self)
          Behaviors.same
      }
    }
    private def handleAuthentication(username: String, password: String,  authDb: ActorRef[KVActor.Command], actorRef: ActorRef[AuthManager.Command])
                            (implicit executionContext: ExecutionContext, timeout: Timeout, scheduler: Scheduler): Unit = {
      val originalSender = actorRef
      // step 3 - ask the actor
      val future: Future[Any] = authDb.ask(KVActor.Read(username, _))
      // step 4 - handle the future for e.g. with onComplete
      future.onComplete {
        // step 5 most important
        // NEVER CALL METHODS ON THE ACTOR INSTANCE OR ACCESS MUTABLE STATE IN ONCOMPLETE.
        // avoid closing over the actor instance or mutable state
        case Success(KVActor.Response(None)) => originalSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Success(KVActor.Response(Some(dbPassword))) =>
          if (dbPassword == password) originalSender ! AuthSuccess
          else originalSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
        case Failure(_) => originalSender ! AuthFailure(AUTH_FAILURE_SYSTEM)
      }
    }
  }

  val root = Behaviors.setup[Nothing] { ctx =>

    import AuthManager._
    val authDb = ctx.spawn(KVActor(), "kvActor")
    val authManager = ctx.spawn(AuthManager(authDb), "authManager")
    authManager ! Authenticate("daniel", "rtjvm")
    authManager ! RegisterUser("daniel", "rtjvm")
    authManager ! Authenticate("daniel", "iloveakka")

    authManager ! RegisterUser("daniel", "rtjvm")
    authManager ! Authenticate("daniel", "rtjvm")

    Behaviors.empty
  }
  ActorSystem[Nothing](root, "askPattern")

}
