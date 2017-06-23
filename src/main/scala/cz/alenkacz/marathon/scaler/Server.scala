package cz.alenkacz.marathon.scaler

import com.typesafe.config.ConfigFactory
import cz.alenkacz.marathon.scaler.ExtendedConfig.getApplicationConfigurationList

object Server {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    // val applications = getApplicationConfigurationList(config, rmqClients)
    // create autoscaler actor
    // create scheduler actor
  }
}
