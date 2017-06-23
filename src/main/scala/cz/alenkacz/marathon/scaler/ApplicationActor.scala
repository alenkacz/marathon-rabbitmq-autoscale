package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging}
import cz.alenkacz.marathon.scaler.ApplicationActor._
import cz.alenkacz.marathon.scaler.RabbitMQApiActor._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import cz.alenkacz.marathon.scaler.Main.logger
import cz.alenkacz.marathon.scaler.MarathonApiActor._

class ApplicationActor(checkPeriod: FiniteDuration, rmqActor: ActorRef, marathonActor: ActorRef, app: Application) extends Actor with ActorLogging with StrictLogging {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(500 millis, checkPeriod, self, CheckScaleStatus)
  implicit val timeout = Timeout(5 seconds) // TODO rmq query timeout

  override def receive: Receive = {
    case CheckScaleStatus =>
      (rmqActor ? MessageCountRequest(app.vhost, app.queueName)).mapTo[MessageCountResponse]
        .map(c => c.count)
        .map(scaleDirection)
        .foreach {
          case Up =>
            logger.debug(
              s"Application's ${app.name} queue '${app.queueName}' is over limit, app will be scaled up")
            self ! ScaleUp
          case Down =>
            logger.debug(s"Application's ${app.name} queue '${app.queueName}' is empty, we can decrease number of instances")
            self ! ScaleDown
          case None =>
            logger.debug(s"No need to scale ${app.name}. Queue message count is inside the limits.")
        }
    case ScaleUp =>
      marathonActor ! MarathonScaleUp(app.name, app.maxInstancesCount)
      // TODO add instances count, publish this after message from marathon client with confirmation is received
      context.system.eventStream.publish(ScaledUp(app))
    case ScaleDown =>
      marathonActor ! MarathonScaleDown(app.name, app.minInstancesCount)
      // TODO add instances count, publish this after message from marathon client with confirmation is received
      context.system.eventStream.publish(ScaledDown(app))
  }

  private def scaleDirection(messageCount: Long): ScaleDirection = {
    messageCount match {
      case _ if messageCount > app.maxMessagesCount => Up
      case _ if messageCount == 0 && app.minInstancesCount.isDefined => Down
      case _ => None
    }
  }
}

object ApplicationActor {
  case object CheckScaleStatus
  case object ScaleUp
  case object ScaleDown

  sealed trait ApplicationEvent
  case class ScaledUp(application: Application) extends ApplicationEvent
  case class ScaledDown(application: Application) extends ApplicationEvent

  sealed trait ScaleDirection
  case object Up extends ScaleDirection
  case object Down extends ScaleDirection
  case object None extends ScaleDirection
}