package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging}
import cz.alenkacz.marathon.scaler.AutoscalerActor._
import scala.concurrent.duration._

class AutoscalerActor(checkPeriod: FiniteDuration,
                      applicationsReCheckPeriod: FiniteDuration)
    extends Actor
    with ActorLogging {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(500 millis,
                                      applicationsReCheckPeriod,
                                      self,
                                      CheckNewApplications)
  // TODO map of applications
  // TODO map of rmq actors

  override def receive: Receive = {
    case CheckNewApplications =>
    // call marathon, check for apps
    // handle child actors - create new ones if needed
  }
}

object AutoscalerActor {
  case object CheckNewApplications
}
