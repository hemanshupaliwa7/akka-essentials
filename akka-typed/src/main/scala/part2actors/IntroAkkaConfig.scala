package part2actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object IntroAkkaConfig extends App {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>

      val actor = ctx.spawn(SimpleLoggingActor(), "SimpleLoggingActorConfigurationDemo")
      actor ! "A message to remember"

      val defaultConfigActor = ctx.spawn(SimpleLoggingActor(), "SimpleLoggingDefaultConfigFileDemo")
      defaultConfigActor ! "Remember me"

      val specialConfigActor = ctx.spawn(SimpleLoggingActor(), "SimpleLoggingSpecialConfigDemo")
      specialConfigActor ! "Remember me, I am special"

      Behaviors.empty
    }
  }
  ActorSystem[Nothing](RootBehavior(), "ConfigurationDemo")

  object SimpleLoggingActor {
    def apply():Behavior[Any] = Behaviors.receive { (ctx, msg) =>
      ctx.log.info(msg.toString)
      Behaviors.same
    }
  }

  /**
   * 1 - inline configuration
   */
  val configString =
    """
      | akka {
      |   loglevel = "ERROR"
      | }
    """.stripMargin

  val config = ConfigFactory.parseString(configString)
  val system = ActorSystem[Nothing](RootBehavior(), "ConfigurationDemo", ConfigFactory.load(config))


  /**
   * 2 - config file
   */
  val defaultConfigSystem = ActorSystem[Nothing](RootBehavior(), "DefaultConfigFileDemo")


  /**
   * 3 - separate config in the same file
   */
  val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
  val specialConfigSystem = ActorSystem[Nothing](RootBehavior(), "SpecialConfigDemo", specialConfig)


  /**
   * 4 - separate config in another file
   */

  val separateConfig = ConfigFactory.load("secretFolder/secretConfiguration.conf")
  println(s"separate config log level: ${separateConfig.getString("akka.loglevel")}")

  /**
   * 5 - different file formats
   * JSON, Properties
   */
  val jsonConfig = ConfigFactory.load("json/jsonConfig.json")
  println(s"json config: ${jsonConfig.getString("aJsonProperty")}")
  println(s"json config: ${jsonConfig.getString("akka.loglevel")}")

  val propsConfig = ConfigFactory.load("props/propsConfiguration.properties")
  println(s"properties config: ${propsConfig.getString("my.simpleProperty")}")
  println(s"properties config: ${propsConfig.getString("akka.loglevel")}")

}
