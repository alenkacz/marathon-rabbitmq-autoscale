package cz.alenkacz.marathon.scaler

import java.time.{Duration, Instant}

import com.rabbitmq.client.Channel
import com.typesafe.config.{Config, ConfigFactory}
import mesosphere.marathon.client.Marathon
import mesosphere.marathon.client.model.v2.{GetAppResponse, GetAppsResponse}
import org.junit.runner.RunWith
import org.mockito.{ArgumentMatcher, ArgumentMatchers, Matchers, Mockito}
import org.scalatest.junit.JUnitRunner
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

@RunWith(classOf[JUnitRunner])
class MainTest extends TestFixture with MockitoSugar {
  it should "not call marathon when limit is not reached" in { fixture =>
    val marathonMock = mock[Marathon]
    Main.checkAndScale(Array(TestApplication("test", "", "/", "test", 10)),
                       fixture.rmqClients,
                       marathonMock,
                       app => false)

    verify(marathonMock, never()).updateApp(ArgumentMatchers.any(),
                                            ArgumentMatchers.any(),
                                            ArgumentMatchers.any())
  }

  it should "call marathon when limit is reached" in { fixture =>
    sendMessages(fixture.rmqClients("").channel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    waitForMessages(
      () => fixture.rmqClients("").messageCount("/", "test").get == 15,
      Duration.ofSeconds(5))
    Main.checkAndScale(Array(TestApplication("test", "", "/", "test", 10)),
                       fixture.rmqClients,
                       marathonMock,
                       app => false)

    verify(marathonMock, atLeastOnce()).updateApp(ArgumentMatchers.any(),
                                                  ArgumentMatchers.any(),
                                                  ArgumentMatchers.any())
  }

  it should "call marathon when queue is empty and scaledown is enabled" in {
    fixture =>
      val marathonMock = mock[Marathon]
      val appResponse = nonEmptyAppResponse()
      when(marathonMock.getApp("test")).thenReturn(appResponse)
      fixture.rmqClients("").purgeQueue("/", "test")
      waitForMessages(
        () => fixture.rmqClients("").messageCount("/", "test").get == 0,
        Duration.ofSeconds(5))
      Main.checkAndScale(Array(
                           TestApplication("test",
                                           "",
                                           "/",
                                           "test",
                                           10,
                                           minInstancesCount = Some(0))),
                         fixture.rmqClients,
                         marathonMock,
                         app => false)

      verify(marathonMock, atLeastOnce()).updateApp(
        ArgumentMatchers.any(),
        ArgumentMatchers.argThat(new AppMatcher(0)),
        ArgumentMatchers.any())
  }

  it should "scale for queue on second rmq instance" in { fixture =>
    sendMessages(fixture.rmqClients("second").channel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    waitForMessages(
      () => fixture.rmqClients("second").messageCount("/", "test").get == 15,
      Duration.ofSeconds(5))
    Main.checkAndScale(
      Array(TestApplication("test", "second", "/", "test", 10)),
      fixture.rmqClients,
      marathonMock,
      app => false)

    verify(marathonMock, atLeastOnce()).updateApp(ArgumentMatchers.any(),
                                                  ArgumentMatchers.any(),
                                                  ArgumentMatchers.any())
  }

  it should "call marathon when limit is reached for application which used non-default RabbitMQ" in {
    fixture =>
      sendMessages(fixture.rmqClients("second").channel, "test", 15)
      val marathonMock = mock[Marathon]
      when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
      waitForMessages(
        () => fixture.rmqClients("second").messageCount("/", "test").get == 15,
        Duration.ofSeconds(5))
      Main.checkAndScale(
        Array(TestApplication("test", "second", "/", "test", 10)),
        fixture.rmqClients,
        marathonMock,
        app => false)

      verify(marathonMock, atLeastOnce()).updateApp(ArgumentMatchers.any(),
                                                    ArgumentMatchers.any(),
                                                    ArgumentMatchers.any())
  }

  it should "not scale to more than maxInstancesCount" in { fixture =>
    sendMessages(fixture.rmqClients("").channel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    Main.checkAndScale(
      Array(TestApplication("test", "", "/", "test", 10, Some(1))),
      fixture.rmqClients,
      marathonMock,
      app => false)

    verify(marathonMock, never()).updateApp(ArgumentMatchers.any(),
                                            ArgumentMatchers.any(),
                                            ArgumentMatchers.any())
  }

  it should "not scale to less than minInstancesCount" in { fixture =>
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    fixture.rmqClients("").purgeQueue("/", "test")
    waitForMessages(
      () => fixture.rmqClients("").messageCount("/", "test").get == 0,
      Duration.ofSeconds(5))
    Main.checkAndScale(Array(
                         TestApplication("test",
                                         "",
                                         "/",
                                         "test",
                                         10,
                                         minInstancesCount = Some(1))),
                       fixture.rmqClients,
                       marathonMock,
                       app => false)

    verify(marathonMock, never()).updateApp(ArgumentMatchers.any(),
                                            ArgumentMatchers.any(),
                                            ArgumentMatchers.any())
  }

  it should "should not scale up when cooled down" in { fixture =>
    sendMessages(fixture.rmqClients("").channel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    waitForMessages(
      () => fixture.rmqClients("").messageCount("/", "test").get == 15,
      Duration.ofSeconds(5))
    Main.checkAndScale(Array(TestApplication("test", "", "/", "test", 10)),
                       fixture.rmqClients,
                       marathonMock,
                       app => true)

    verify(marathonMock, never()).updateApp(ArgumentMatchers.any(),
                                            ArgumentMatchers.any(),
                                            ArgumentMatchers.any())
  }

  it should "return list of scaled apps" in { fixture =>
    sendMessages(fixture.rmqClients("second").channel, "test", 15)
    val marathonMock = mock[Marathon]
    val application = TestApplication("test", "second", "/", "test", 10)
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    waitForMessages(
      () => fixture.rmqClients("second").messageCount("/", "test").get == 15,
      Duration.ofSeconds(5))
    val actual = Main.checkAndScale(Array(application),
                                    fixture.rmqClients,
                                    marathonMock,
                                    app => false)

    actual should be(Seq(application))
  }

  it should "be cooled down" in { fixture =>
    val actual =
      Main.isCooledDown(TestApplication("test", "", "/", "test", 10),
                        Map("test" -> Instant.ofEpochMilli(10000)),
                        Instant.MIN,
                        Duration.ofMillis(1),
                        5)

    actual should be(true)
  }

  it should "not be cooled down" in { fixture =>
    val actual =
      Main.isCooledDown(TestApplication("test", "", "/", "test", 10),
                        Map("test" -> Instant.MIN),
                        Instant.MAX,
                        Duration.ofMillis(1),
                        5)

    actual should be(false)
  }

  it should "not throw exception for single rabbitmq server" in { _ =>
    noException should be thrownBy Main.setupRabbitMqClients(
      ConfigFactory.load("application"))
  }

  it should "throw validation exception for multiple rmq servers without name" in {
    _ =>
      an[InvalidConfigurationException] should be thrownBy Main
        .setupRabbitMqClients(ConfigFactory.load("multiple-rmq-no-name"))
  }

  it should "allow to have one rmq server without name" in { _ =>
    noException should be thrownBy Main.setupRabbitMqClients(
      ConfigFactory.load("multiple-rmq-with-names"))
  }

  def limitReached(millis: Long, duration: Duration) =
    System.currentTimeMillis() - millis > duration.toMillis

  def waitForMessages(b: () => Boolean, duration: Duration) = {
    val millis = System.currentTimeMillis()
    while (!b() && !limitReached(millis, duration)) {}
  }

  private def sendMessages(rmqChannel: Channel,
                           queueName: String,
                           number: Int): Unit = {
    1 to number foreach { _ =>
      rmqChannel.basicPublish("",
                              queueName,
                              false,
                              false,
                              null,
                              "test".getBytes)
    }
    rmqChannel.waitForConfirmsOrDie()
  }

  class AppWithInstancesCount(count: Int)
      extends ArgumentMatcher[mesosphere.marathon.client.model.v2.App] {
    override def matches(
        argument: mesosphere.marathon.client.model.v2.App): Boolean =
      argument.getInstances == count
  }

  private def nonEmptyAppResponse(instancesCount: Int = 1) = {
    val response = new GetAppResponse
    response.setApp(new mesosphere.marathon.client.model.v2.App)
    response.getApp.setInstances(instancesCount)
    response
  }

  class AppMatcher(instancesCount: Int)
      extends ArgumentMatcher[mesosphere.marathon.client.model.v2.App] {
    override def matches(
        argument: mesosphere.marathon.client.model.v2.App): Boolean =
      argument.getInstances.toInt == instancesCount
  }
}

case class TestApplication(name: String,
                           rmqServerName: String,
                           vhost: String,
                           queueName: String,
                           maxMessagesCount: Int,
                           maxInstancesCount: Option[Int] = None,
                           minInstancesCount: Option[Int] = None)
  extends Application
