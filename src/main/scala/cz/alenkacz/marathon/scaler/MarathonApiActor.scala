package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging}
import cz.alenkacz.marathon.scaler.MarathonApiActor._
import mesosphere.marathon.client.Marathon

import scala.concurrent.{ExecutionContext, Future}

class MarathonApiActor(marathonClient: Marathon)(implicit val executionContext: ExecutionContext) extends Actor with ActorLogging {
  override def receive: Receive = {
    case MarathonScaleUp(appName, maxInstancesCount) =>
      Future {
        MarathonProxy.scaleUp(marathonClient, appName, maxInstancesCount)
      }
  }
}

object MarathonApiActor {
  sealed trait ApiMessage
  case class MarathonScaleUp(applicationName: String, maxInstancesCount: Option[Int] = None)
}
