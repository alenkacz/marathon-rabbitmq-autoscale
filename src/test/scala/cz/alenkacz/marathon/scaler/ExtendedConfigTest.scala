package cz.alenkacz.marathon.scaler

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class ExtendedConfigTest extends TestFixture {
  it should "start also when no applications are specified" in { fixture =>
    val actual = ExtendedConfig.getApplicationConfigurationList(ConfigFactory.load("without-applications"), fixture.rmqClient)

    actual.isEmpty should be (true)
  }

  it should "return application configuration list without apps with non-existing queues" in { fixture =>
    val actual = ExtendedConfig.getApplicationConfigurationList(ConfigFactory.load("with-non-existing-queues"), fixture.rmqClient)

    actual.isEmpty should be (true)
  }
}
