package cz.alenkacz.marathon.scaler

import com.rabbitmq.client.Channel
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
    Main.checkAndScale(Array(Application("test", "test", 10)), fixture.rmqChannel, marathonMock)

    verify(marathonMock, never()).updateApp(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
  }

  it should "call marathon when limit is reached" in { fixture =>
    sendMessages(fixture.rmqChannel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    Main.checkAndScale(Array(Application("test", "test", 10)), fixture.rmqChannel, marathonMock)

    verify(marathonMock, atLeastOnce()).updateApp(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())
  }

  it should "not scale to more than maxInstancesCount" in { fixture =>
    sendMessages(fixture.rmqChannel, "test", 15)
    val marathonMock = mock[Marathon]
    when(marathonMock.getApp("test")).thenReturn(nonEmptyAppResponse())
    Main.checkAndScale(Array(Application("test", "test", 10, Some(1))), fixture.rmqChannel, marathonMock)

    verify(marathonMock, atLeastOnce()).updateApp(ArgumentMatchers.any(), ArgumentMatchers.argThat(new AppWithInstancesCount(1)), ArgumentMatchers.any())
  }

  private def sendMessages(rmqChannel: Channel, queueName: String, number: Int): Unit = {
    1 to number foreach {_ => rmqChannel.basicPublish("", queueName, false, false, null, "test".getBytes) }
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
}