package cz.alenkacz.marathon.scaler

import com.typesafe.scalalogging.StrictLogging
import mesosphere.marathon.client.Marathon
import collection.JavaConverters._

object MarathonProxy extends StrictLogging {
  def scaleUp(marathonClient: Marathon, applicationName: String, maxInstancesCount: Option[Int]): Unit = {
    val applicationState = marathonClient.getApp(applicationName).getApp
    val targetInstanceCount = Math.min(applicationState.getInstances + 1, maxInstancesCount.getOrElse(Integer.MAX_VALUE))
    logger.info(s"Current instances count of application $applicationName is ${applicationState.getInstances} and will be increased to $targetInstanceCount")

    applicationState.setInstances(targetInstanceCount)
    marathonClient.updateApp(applicationName, applicationState, true)
  }

  val QUEUE_LABEL_NAME = "AUTOSCALE_QUEUE"
  val MAX_MESSAGES_LABEL_NAME = "AUTOSCALE_MAXMESSAGES"
  val MAXINSTANCES_LABEL_NAME = "AUTOSCALE_MAXINSTANCES"

  def findAppsWithAutoscaleLabels(marathonClient: Marathon): Seq[Application] = {
    val labelledApps = marathonClient.getApps.getApps.asScala
      .filter(a => a.getLabels != null && a.getLabels.asScala.exists(p => p._1.equalsIgnoreCase(QUEUE_LABEL_NAME) || p._1.equalsIgnoreCase(MAX_MESSAGES_LABEL_NAME)))
      .map(a => {
        val labels = a.getLabels.asScala
        val queueName = labels.find(_._1.equalsIgnoreCase(QUEUE_LABEL_NAME)).map(_._2).get
        val maxMessagesCount = labels.find(_._1.equalsIgnoreCase(MAX_MESSAGES_LABEL_NAME)).map(_._2).get.toInt
        val maxInstancesCount = labels.find(_._1.equalsIgnoreCase(MAXINSTANCES_LABEL_NAME)).map(_._2).map(_.toInt)
        Application(a.getId, queueName, maxMessagesCount, maxInstancesCount)
      })

    logger.info(s"Configured following apps via marathon labels: '${labelledApps.map(a => a.name).mkString(",")}'")
    labelledApps
  }
}
