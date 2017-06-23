package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cz.alenkacz.marathon.scaler.ApplicationActor.ScaledUp
import cz.alenkacz.marathon.scaler.MarathonApiActor.MarathonScaleUp
import cz.alenkacz.marathon.scaler.RabbitMQApiActor._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class ApplicationActorTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  it should "scale up when number of messages is high" in {
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor))
    system.eventStream.subscribe(self, classOf[ScaledUp])
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, system.actorOf(Props.empty), application)))

    within(2 seconds) {
      expectMsg(ScaledUp(application))
      applicationController ! PoisonPill
    }
  }

  it should "not scale up when number of messages is low" in {
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor(0)))
    system.eventStream.subscribe(self, classOf[ScaledUp])
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, system.actorOf(Props.empty), application)))

    within(2 seconds) {
      expectNoMsg()
      applicationController ! PoisonPill
    }
  }

  it should "call scale up on marathon when scaling up" in {
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor))
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, testActor, application)))

    within(2 seconds) {
      expectMsg(MarathonScaleUp(application.name))
      applicationController ! PoisonPill
    }
  }
}

class TestRabbitMQApiActor(msgCount: Int = 10) extends Actor with ActorLogging {
  override def receive: Receive = {
    case MessageCountRequest(_, _) =>
      println(msgCount)
      sender() ! MessageCountResponse(msgCount)
  }
}

class NoOpActor extends Actor {
  override def receive: Receive = {
    case _ =>
  }
}
