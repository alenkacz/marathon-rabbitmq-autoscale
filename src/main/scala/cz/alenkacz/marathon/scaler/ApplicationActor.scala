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
import cz.alenkacz.marathon.scaler.MarathonApiActor._

class ApplicationActor(checkPeriod: FiniteDuration,
                       rmqActor: ActorRef,
                       marathonActor: ActorRef,
                       app: Application,
                       coolDownTicks: Int)
    extends FSM[State, Data]
    with StrictLogging {
  import context.dispatcher
  val tick =
    context.system.scheduler.schedule(500 millis,
                                      checkPeriod,
                                      self,
                                      CheckScaleStatus)
  implicit val timeout = Timeout(5 seconds) // TODO rmq query timeout

  startWith(Normal, Data(None))

  when(Normal) {
    case Event(CheckScaleStatus, _) =>
      (rmqActor ? MessageCountRequest(app.vhost, app.queueName))
        .mapTo[MessageCountResponse]
        .map(c => c.count)
        .map(scaleDirection)
        .foreach {
          case Up =>
            logger.debug(
              s"Application's ${app.name} queue '${app.queueName}' is over limit, app will be scaled up")
            self ! ScaleUp
          case Down =>
            logger.debug(
              s"Application's ${app.name} queue '${app.queueName}' is empty, we can decrease number of instances")
            self ! ScaleDown
          case NoDirection =>
            logger.debug(
              s"No need to scale ${app.name}. Queue message count is inside the limits.")
        }
      stay
    case Event(ScaleUp, _) =>
      marathonActor ! MarathonScaleUp(app.name, app.maxInstancesCount)
      // TODO add instances count, publish this after message from marathon client with confirmation is received
      context.system.eventStream.publish(ScaledUp(app))
      goto(CoolDown) using Data(Some(0))
    case Event(ScaleDown, _) =>
      marathonActor ! MarathonScaleDown(app.name, app.minInstancesCount)
      // TODO add instances count, publish this after message from marathon client with confirmation is received
      context.system.eventStream.publish(ScaledDown(app))
      goto(CoolDown) using Data(Some(0))
  }

  when(CoolDown) {
    case Event(CheckScaleStatus, Data(Some(count))) =>
      if (count >= coolDownTicks) {
        goto(Normal) using Data(None)
      } else {
        stay using Data(Some(count + 1))
      }
    case Event(CheckScaleStatus, Data(None)) =>
      throw new IllegalArgumentException(
        "When cooldown, cooldown times count should be specified")
  }

  private def scaleDirection(messageCount: Long): ScaleDirection = {
    messageCount match {
      case _ if messageCount > app.maxMessagesCount => Up
      case _ if messageCount == 0 && app.minInstancesCount.isDefined => Down
      case _ => NoDirection
    }
  }
}

object ApplicationActor {
  sealed trait State
  case object Normal extends State
  case object CoolDown extends State

  case class Data(coolDownTimes: Option[Int])

  case object CheckScaleStatus
  case object ScaleUp
  case object ScaleDown

  sealed trait ApplicationEvent
  case class ScaledUp(application: Application) extends ApplicationEvent
  case class ScaledDown(application: Application) extends ApplicationEvent

  sealed trait ScaleDirection
  case object Up extends ScaleDirection
  case object Down extends ScaleDirection
  case object NoDirection extends ScaleDirection
}
