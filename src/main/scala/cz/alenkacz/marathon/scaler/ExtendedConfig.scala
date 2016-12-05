package cz.alenkacz.marathon.scaler

import java.time.Duration

import com.typesafe.config.Config
import cz.alenkacz.marathon.scaler.rabbitmq.Client
import scala.collection.JavaConverters._

object ExtendedConfig {
  def getApplicationConfigurationList(config: Config, rabbitMqClients: Map[String, Client]): Seq[Application] = {
    if (config.hasPath("applications")) {
      config.getConfigList("applications").asScala.map(a => ApplicationFactory.tryCreate(rabbitMqClients(a.getOptionalString("rmqServerName").getOrElse("")), a.getString("name"), a.getOptionalString("rmqServerName").getOrElse(""), a.getOptionalString("vhost").getOrElse("/"), a.getString("queue"), a.getInt("maxMessagesCount"), a.getOptionalInt("maxInstancesCount"), a.getOptionalInt("minInstancesCount"))).filter(_.isSuccess).map(_.get)
    } else {
      Seq.empty
    }
  }

  implicit class RichConfig(val underlying: Config) extends AnyVal {
    def getOptionalInt(path: String): Option[Int] = if (underlying.hasPath(path)) {
      Some(underlying.getInt(path))
    } else {
      None
    }

    def getOptionalString(path: String): Option[String] = if (underlying.hasPath(path)) {
      Some(underlying.getString(path))
    } else {
      None
    }

    def getOptionalDuration(path: String): Option[Duration] = if (underlying.hasPath(path)) {
      Some(underlying.getDuration(path))
    } else {
      None
    }
  }
}
