package part4faulttolerance.receptionistexamples

import akka.actor.typed.ActorSystem
object ReceptionistDemo extends App {

  // create the ActorSystem
  val supervisor: ActorSystem[Supervisor.SupervisorMessage] = ActorSystem(
    Supervisor(),
    "Supervisor"
  )

  // send the Start message to the Supervisor
  supervisor ! Supervisor.Start

  // wait a few moments, and then stop the Supervisor
  Thread.sleep(200)
  supervisor.terminate()

}