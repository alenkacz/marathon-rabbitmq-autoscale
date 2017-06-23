package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging}
import cz.alenkacz.marathon.scaler.ApplicationActor._
import cz.alenkacz.marathon.scaler.RabbitMQApiActor._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import cz.alenkacz.marathon.scaler.MarathonApiActor.MarathonScaleUp

class ApplicationActor(checkPeriod: FiniteDuration, rmqActor: ActorRef, marathonActor: ActorRef, application: Application) extends Actor with ActorLogging {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(500 millis, checkPeriod, self, CheckScaleStatus)
  implicit val timeout = Timeout(5 seconds) // TODO rmq query timeout

  override def receive: Receive = {
    case CheckScaleStatus =>
      (rmqActor ? MessageCountRequest(application.vhost, application.queueName)).mapTo[MessageCountResponse]
        .map(c => c.count > application.maxMessagesCount)
        .filter(c => c)
        .foreach(c => self ! ScaleUp)
    case ScaleUp =>
      marathonActor ! MarathonScaleUp(application.name, application.maxInstancesCount)
      // TODO add instances count, publish this after message from marathon client with confirmation is received
      context.system.eventStream.publish(ScaledUp(application))
    case ScaleDown =>
      // TODO
  }
}

object ApplicationActor {
  case object CheckScaleStatus
  case object ScaleUp
  case object ScaleDown

  sealed trait ApplicationEvent
  case class ScaledUp(application: Application)
}