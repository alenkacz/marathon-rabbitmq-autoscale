package cz.alenkacz.marathon.scaler

case class Application(name: String, queueName: String, maxMessagesCount: Int, maxInstancesCount: Option[Int] = None)
