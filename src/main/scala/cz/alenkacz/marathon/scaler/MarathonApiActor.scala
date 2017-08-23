package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging}
import cz.alenkacz.marathon.scaler.MarathonApiActor._
import cz.alenkacz.marathon.scaler.rabbitmq.Client
import mesosphere.marathon.client.Marathon

import scala.concurrent.{ExecutionContext, Future}

class MarathonApiActor(marathonClient: Marathon, rabbitMqClients: Map[String, Client])(
    implicit val executionContext: ExecutionContext)
    extends Actor
    with ActorLogging {
  override def receive: Receive = {
    case MarathonScaleUp(appName, maxInstancesCount) =>
      Future {
        MarathonProxy.scaleUp(marathonClient, appName, maxInstancesCount)
      }
    case MarathonScaleDown(appName, minInstancesCount) =>
      Future {
        MarathonProxy.scaleDown(marathonClient, appName, minInstancesCount)
      }
    case FindAutoscaleApps =>
      Future {
        sender ! MarathonProxy.findAppsWithAutoscaleLabels(marathonClient, rabbitMqClients)
      }
  }
}

object MarathonApiActor {
  sealed trait ApiMessage
  case class MarathonScaleUp(applicationName: String,
                             maxInstancesCount: Option[Int] = None)
      extends ApiMessage
  case class MarathonScaleDown(applicationName: String,
                               minInstancesCount: Option[Int] = None)
      extends ApiMessage
  case object FindAutoscaleApps extends ApiMessage
  case class AutoscaleAppsResponse(apps: Seq[Application]) extends ApiMessage
}
