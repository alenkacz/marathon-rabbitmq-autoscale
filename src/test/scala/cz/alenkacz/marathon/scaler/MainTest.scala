package cz.alenkacz.marathon.scaler

import java.time.Duration

import com.rabbitmq.client.Channel
import com.typesafe.config.{Config, ConfigFactory}
import mesosphere.marathon.client.Marathon
import mesosphere.marathon.client.model.v2.{GetAppResponse, GetAppsResponse}
import org.junit.runner.RunWith
import org.mockito.{ArgumentMatcher, ArgumentMatchers, Matchers, Mockito}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._

@RunWith(classOf[JUnitRunner])
class MainTest extends TestFixture with MockitoSugar {
  it should "not call marathon when limit is not reached" in { fixture =>
    val marathonMock = mock[Marathon]
    Main.checkAndScale(Array(TestApplication("test", "/", "test", 10)), fixture.rmqClient, marathonMock)

    verify(marathonMock, never()).updateApp(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
  }

  def limitReached(millis: Long, duration: Duration) = System.currentTimeMillis() - millis > duration.toMillis

  def waitForMessages(b: () => Boolean, duration: Duration) = {
    val millis = System.currentTimeMillis()
    while (!b() && !limitReached(millis, duration)) {

    }
  }

  it should "call marathon when limit is reached" in { fixture =>
    sendMessages(fixture.rmqChannel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    waitForMessages(() => fixture.rmqClient.getQueue("/", "test").getTotalMessages == 15, Duration.ofSeconds(5))
    Main.checkAndScale(Array(TestApplication("test", "/", "test", 10)), fixture.rmqClient, marathonMock)

    verify(marathonMock, atLeastOnce()).updateApp(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
  }

  it should "not scale to more than maxInstancesCount" in { fixture =>
    sendMessages(fixture.rmqChannel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    Main.checkAndScale(Array(TestApplication("test", "/", "test", 10, Some(1))), fixture.rmqClient, marathonMock)

    verify(marathonMock, never()).updateApp(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
  }

  it should "start also when no applications are specified" in { fixture =>
    val actual = Main.getApplicationConfigurationList(ConfigFactory.load("without-applications"), fixture.rmqClient)

    actual.isEmpty should be (true)
  }

  it should "return application configuration list without apps with non-existing queues" in { fixture =>
    val actual = Main.getApplicationConfigurationList(ConfigFactory.load("with-non-existing-queues"), fixture.rmqClient)

    actual.isEmpty should be (true)
  }

  private def sendMessages(rmqChannel: Channel, queueName: String, number: Int): Unit = {
    1 to number foreach {_ =>
      rmqChannel.basicPublish("", queueName, false, false, null, "test".getBytes)
    }
    rmqChannel.waitForConfirmsOrDie()
  }

  class AppWithInstancesCount(count: Int) extends ArgumentMatcher[mesosphere.marathon.client.model.v2.App] {
    override def matches(argument: mesosphere.marathon.client.model.v2.App): Boolean = argument.getInstances == count
  }

  private def nonEmptyAppResponse(instancesCount: Int = 1) = {
    val response = new GetAppResponse
    response.setApp(new mesosphere.marathon.client.model.v2.App)
    response.getApp.setInstances(instancesCount)
    response
  }

  case class TestApplication(name: String, vhost: String, queueName: String, maxMessagesCount: Int, maxInstancesCount: Option[Int] = None) extends Application
}