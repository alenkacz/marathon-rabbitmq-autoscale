package cz.alenkacz.marathon.scaler

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class ApplicationFactoryTest extends TestFixture with MockitoSugar {
  it should "create Application when queue exists" in { fixture =>
    val actual = ApplicationFactory.tryCreate(fixture.rmqClient, "test", "/", "test", 10)
    actual.isSuccess should be (true)
    actual.get.name should be ("test")
  }

  it should "not create Application when queue does not exists" in { fixture =>
    val actual = ApplicationFactory.tryCreate(fixture.rmqClient, "test", "/", "non-existing-queue", 10)
    actual.isSuccess should be (false)
  }
}
