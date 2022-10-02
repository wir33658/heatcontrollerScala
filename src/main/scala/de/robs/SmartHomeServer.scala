package de.robs

import akka.actor.{ActorSystem, ClassicActorSystemProvider}
import akka.http.scaladsl.model.DateTime
import akka.stream.{ActorMaterializer, Materializer, SystemMaterializer}
import com.typesafe.scalalogging.Logger
import de.robs.heatcontroller.HeatController
import de.robs.http.HttpRoutes
import de.robs.mqtt.{Error, MqttSub}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object SmartHomeServer extends App {
  implicit val ac: ActorSystem = ActorSystem("smarthomeserver")
  implicit val ec: ExecutionContext = ac.dispatcher
  implicit def matFromSystem(implicit provider: ClassicActorSystemProvider): Materializer = SystemMaterializer(provider.classicSystem).materializer
//  implicit val materializer = ActorMaterializer()
  implicit val log = Logger("SmartHomeServer")

  println(s"sudo java -jar headcontroller-assembly-0.1.0-SNAPSHOT.jar : <HOST> <PORT> <HOST-MQTT> <PORT-MQTT> (default : 192.168.178.52 8080 192.168.178.52 1883)")
  print(s"sudo java -jar headcontroller-assembly-0.1.0-SNAPSHOT.jar : ")
  args.foreach(e => print(s"$e "))
  println()

  val hostOpt = if(args.length > 0)Some(args(0)) else None
  val portOpt = if(args.length > 1)Some(args(1).toInt) else None
  val hostMqttOpt = if(args.length > 2)Some(args(2)) else None
  val portMqttOpt = if(args.length > 3)Some(args(3).toInt) else None
//  val sim = if(args.length > 2){if(args(2) == "false")false else true} else true

  val hc     = new HeatController(sim = false)
  val routes = new HttpRoutes(hostOpt, portOpt, hc)
  var mqtSub: Option[MqttSub] = None

  ac.scheduler.scheduleAtFixedRate(5 seconds, 12 hours)(new Runnable{
    override def run(): Unit = {
      log.info(s"MqttSub reached its life-cycle -> recreating it ...).")
      initMqttSub()
    }
  })

  ac.scheduler.scheduleAtFixedRate(5 seconds, 10 seconds)(new Runnable{
    override def run(): Unit = {
      mqtSub.foreach{ mq =>
        if(mq.problems() > 3){
          log.info(s"MqttSub hat already more then 3 problems -> recreating it ...).")
          initMqttSub()
        }
      }
    }
  })

  var recreationCounter = 1
  protected def initMqttSub(): Unit = {
    try {
      mqtSub match {
        case Some(ms) =>
          try {
            ms.close()
          } catch {
            case t: Throwable =>
              log.error(t.getMessage, t)
              log.error("Continue ...")
          }
          log.info(s"MqttSub re-created ($recreationCounter time).")
          mqtSub = Some(new MqttSub(hc, hostMqttOpt, portMqttOpt))
          recreationCounter = recreationCounter + 1
        case None     =>
          log.info("MqttSub created the very first time.")
          mqtSub = Some(new MqttSub(hc, hostMqttOpt, portMqttOpt))
      }
    } catch {
      case t: Throwable =>
        log.error(t.getMessage, t)
        log.error("Continue ...")
      //   lastErrorBuffer.add(Error(s"WARNING (on Scheduler.run will be continued) : ${t.getMessage}", DateTime.now))
      //        newErrorSinceLastPublish.set(true)
    }
  }

}
