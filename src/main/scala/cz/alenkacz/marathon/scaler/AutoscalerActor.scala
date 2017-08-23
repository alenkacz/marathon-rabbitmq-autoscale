package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging, ActorRef}
import cz.alenkacz.marathon.scaler.AutoscalerActor._
import cz.alenkacz.marathon.scaler.MarathonApiActor.{AutoscaleAppsResponse, FindAutoscaleApps}

import scala.concurrent.duration._
import akka.pattern.ask

class AutoscalerActor(marathonActor: ActorRef, checkPeriod: FiniteDuration,
                      applicationsReCheckPeriod: FiniteDuration)
    extends Actor
    with ActorLogging {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(500 millis,
                                      applicationsReCheckPeriod,
                                      self,
                                      CheckNewApplications)
  var applications = Map.empty[Application, ActorRef]
  // TODO map of applications
  // TODO map of rmq actors

  override def receive: Receive = {
    case CheckNewApplications =>
      (marathonActor ? FindAutoscaleApps).mapTo[AutoscaleAppsResponse]
      // find nonexisting apps
    // find new apps
    // kill nonexisting
    // spin up new actors
  }
}

object AutoscalerActor {
  case object CheckNewApplications
}
