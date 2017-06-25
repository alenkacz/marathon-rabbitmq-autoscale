package cz.alenkacz.marathon.scaler

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit, TestProbe}
import cz.alenkacz.marathon.scaler.ApplicationActor._
import cz.alenkacz.marathon.scaler.MarathonApiActor._
import cz.alenkacz.marathon.scaler.RabbitMQApiActor._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class ApplicationActorTest extends TestKit(ActorSystem("MySpec")) with FlatSpecLike with Matchers with BeforeAndAfterAll {
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  it should "scale up when number of messages is high" in {
    val testProbe = TestProbe()
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor))
    system.eventStream.subscribe(testProbe.testActor, classOf[ScaledUp])
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, system.actorOf(Props.empty), application, 1)))

    within(2 seconds) {
      testProbe.expectMsg(ScaledUp(application))
      applicationController ! PoisonPill
    }
  }

  it should "not scale up when number of messages is low" in {
    val testProbe = TestProbe()
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor(1)))
    system.eventStream.subscribe(testProbe.testActor, classOf[ScaledUp])
    val application = TestApplication("test", "", "/", "test", 2)
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, system.actorOf(Props.empty), application, 1)))

    within(2 seconds) {
      expectNoMsg()
      applicationController ! PoisonPill
    }
  }

  it should "call scale up on marathon when scaling up" in {
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor))
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, testActor, application, 1)))

    within(2 seconds) {
      expectMsg(MarathonScaleUp(application.name))
      applicationController ! PoisonPill
    }
  }

  it should "scale down when queue is empty" in {
    val testProbe = TestProbe()
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor(0)))
    system.eventStream.subscribe(testProbe.testActor, classOf[ScaledDown])
    val application = TestApplication("test", "", "/", "test", 10, minInstancesCount = Some(1))
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, system.actorOf(Props.empty), application, 1)))

    within(2 seconds) {
      testProbe.expectMsg(ScaledDown(application))
      applicationController ! PoisonPill
    }
  }

  it should "call scale down on marathon when scaling up" in {
    val rmqActor = system.actorOf(Props(new TestRabbitMQApiActor))
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationController = system.actorOf(
      Props(new ApplicationActor(5 seconds, rmqActor, testActor, application, 1)))
    applicationController ! ScaleDown

    within(300 millis) {
      expectMsg(MarathonScaleDown(application.name))
      applicationController ! PoisonPill
    }
  }

  it should "go to cooldown state after scaling up" in {
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationActor = TestFSMRef(new ApplicationActor(5 seconds, system.actorOf(Props.empty), system.actorOf(Props.empty), application, 1))
    applicationActor ! ScaleUp

    applicationActor.stateName should be(CoolDown)
  }

  it should "stay in cooldown state for number of ticks" in {
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationActor = TestFSMRef(new ApplicationActor(5 seconds, system.actorOf(Props.empty), system.actorOf(Props.empty), application, 3))
    applicationActor ! ScaleUp
    applicationActor ! CheckScaleStatus
    applicationActor ! CheckScaleStatus

    applicationActor.stateName should be(CoolDown)
    applicationActor.stateData should be(Data(Some(2)))
  }

  it should "go from cooldown state back to normal state after number of ticks" in {
    val application = TestApplication("test", "", "/", "test", 1)
    val applicationActor = TestFSMRef(new ApplicationActor(5 seconds, system.actorOf(Props.empty), system.actorOf(Props.empty), application, 1))
    applicationActor ! ScaleUp
    applicationActor ! CheckScaleStatus
    applicationActor ! CheckScaleStatus

    applicationActor.stateName should be(Normal)
    applicationActor.stateData should be(Data(None))
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
}
