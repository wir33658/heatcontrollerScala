package de.robs.mqtt

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.DateTime
import de.robs.CircularBuffer
import de.robs.heatcontroller.HeatController
import spray.json.JsonParser
import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger
import org.eclipse.paho.client.mqttv3.{IMqttDeliveryToken, MqttCallback}

import scala.concurrent.ExecutionContext
import spray.json._

case class Error(error: String, time: DateTime)
class MqttSub(val hc: HeatController, hostOpt: Option[String] = None, portOpt: Option[Int] = None)(implicit val ac: ActorSystem, val log: Logger) extends MqttConnector {
  import org.eclipse.paho.client.mqttv3.MqttClient
  import org.eclipse.paho.client.mqttv3.MqttConnectOptions
  import org.eclipse.paho.client.mqttv3.MqttException
  import org.eclipse.paho.client.mqttv3.MqttMessage
  import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
  import scala.concurrent.duration._

  implicit val ec: ExecutionContext = ac.dispatcher

  val host = hostOpt.getOrElse("192.168.178.52")
  val port = portOpt.getOrElse(1883)

  val baseTopic = "/home/heatcontroller"
  val qos = 2
  val broker = s"tcp://$host:$port" // Raspi
  val persistence = new MemoryPersistence

  val lastErrorBuffer = new CircularBuffer[Error]()

  val client = new MqttClient(broker, MqttClient.generateClientId, persistence)
  log.info("Connecting to broker: " + broker)
  client.connect
  log.info("Connected")
  setCallback(client)
  log.info("Callback set.")
  client.subscribe(baseTopic)
  log.info(s"Subscribed to topic ''$baseTopic")

  /*
  val newErrorSinceLastPublish = new AtomicBoolean(true)
  val clientCancellable = ac.scheduler.schedule(20 seconds, 2 minutes, new Runnable{
    override def run(): Unit = {
      if(newErrorSinceLastPublish.get() && clientOpt.isDefined){
        publishError()(clientOpt.get._1)
        newErrorSinceLastPublish.set(false)
      }
    }
  })
   */

//  val clientCancellable = ac.scheduler.schedule(20 seconds, 2 minutes, new Runnable{
  val clientCancellable = ac.scheduler.scheduleAtFixedRate(20 seconds, 2 minutes)(new Runnable{
    override def run(): Unit = try {
      publishState()
    } catch {
      case t: Throwable =>
        log.error(t.getMessage, t)
        log.error("Continue ...")
        lastErrorBuffer.add(Error(s"WARNING (on Scheduler.run will be continued) : ${t.getMessage}", DateTime.now))
//        newErrorSinceLastPublish.set(true)
    }
  })

  hc.calibrate()

  def close(): Unit = {
    if(client != null) {
      log.info(s"Closing ...")
      clientCancellable.cancel()
      client.unsubscribe(baseTopic)
      client.setCallback(null)
      client.disconnectForcibly(2000, 2000, true)
      client.close(true)
      log.info(s"... closed.")
    } else log.warn(s"Client = NULL (however this can happend)")
  }

  /*
  try {
    while(true) {
      try {
        if(clientOpt.isEmpty){
          clientOpt = Some(connect())
          hc.calibrate()
        }
        Thread.sleep(2000)
      } catch {
        case e: SocketException => handleReconnectError(e)
        case e: EOFException    => handleReconnectError(e)
      }
    }
  } catch {
    case me: MqttException =>
      log.info("reason " + me.getReasonCode)
      log.info("msg " + me.getMessage)
      log.info("loc " + me.getLocalizedMessage)
      log.info("cause " + me.getCause)
      log.info("excep " + me)
      log.error(me, me.getMessage)
      if(clientOpt.isDefined){
        lastErrorBuffer.add(Error(s"ERROR (System.exit) : ${me.getMessage}", DateTime.now))
        newErrorSinceLastPublish.set(true)
      }
      Thread.sleep(2000)
      System.exit(0)
    case t: Throwable =>
      log.error(t, t.getMessage)
      if(clientOpt.isDefined){
        lastErrorBuffer.add(Error(s"ERROR (System.exit) : ${t.getMessage}", DateTime.now))
        newErrorSinceLastPublish.set(true)
      }
      Thread.sleep(2000)
      System.exit(0)
  }
  */


  def publishState(): Unit = {
    val jss = hc.cb.ls.map{log =>
      val time = log.time
      val longTime = time.clicks
      JsObject(Map(
        "lastCommand" -> JsString(log.lastCommand),
        "recentTemp" -> JsNumber(log.recentTemp),
        "minTemp" -> JsString(log.minTemp.toString),
        "maxTemp" -> JsString(log.maxTemp.toString),
        "halfDegreeStep" -> JsString(log.halfDegreeStep.toString),
        "triggerStep" -> JsString(log.triggerStep.toString),
        "time" -> JsString(time.toIsoDateTimeString()),
        "longTime" -> JsNumber(longTime)
      ))
    }
    val json = JsObject(Map("state" -> JsArray(jss)))
    send(json, s"$baseTopic/state")
  }

  def publishError(): Unit = {
    val jss = lastErrorBuffer.ls.map{ error =>
      JsObject(Map("error" -> JsString(error.error), "time" -> JsString(error.time.toIsoDateTimeString())))
    }
    val json = JsObject(Map("errors" -> JsArray(jss)))
    send(json, s"$baseTopic/errors")
  }

  /*
  def connect(): (MqttClient, Cancellable) = {
    val client = new MqttClient(broker, MqttClient.generateClientId, persistence)
    //    val connOpts = new MqttConnectOptions
    //    connOpts.setCleanSession(true)
    log.info("Connecting to broker: " + broker)
    //    sampleClient.connect(connOpts)
    client.connect
    log.info("Connected")
    setCallback(client)
    client.subscribe(baseTopic)

    val cl = ac.scheduler.schedule(20 seconds, 2 minutes, new Runnable{
      override def run(): Unit = try {
        publishState(client)
      } catch {
        case t: Throwable =>
          log.error(t, t.getMessage)
          log.error("Continue ...")
          lastErrorBuffer.add(Error(s"WARNING (on Scheduler.run will be continued) : ${t.getMessage}", DateTime.now))
          newErrorSinceLastPublish.set(true)
      }
    })

    (client, cl)
  }
  */

  /*
  def disconnect(client: MqttClient, cl: Cancellable): Unit = {
    cl.cancel()
    client.disconnect()
    client.close()
    log.info("Disconnected")
    //    System.exit(0)
  }
   */

  def setCallback(implicit client: MqttClient): Unit = {
    client.setCallback(new MqttCallback {
      override def connectionLost(cause: Throwable): Unit = {
        log.error("connection lost", cause)
      }
      override def messageArrived(topic: String, message: MqttMessage): Unit = try {
        log.info(s"MessageArrived : $topic : $message")
        if(topic == baseTopic) {
          val payload = message.getPayload
          val pi = ParserInput.apply(payload)
          val jv = JsonParser.apply(pi)
          val jsValues = jv.asJsObject.fields
          val params = jsValues.get("params") match {
            case Some(jsParams) => jsParams.asInstanceOf[JsString].value match {
              case params: String => params.split(',').toSeq
              case other => Seq.empty[String]
            }
            case None => Seq.empty[String]
          }
          jsValues.get("cmd") match {
            case Some(jsCmd) => jsCmd.asInstanceOf[JsString].value match {
              case "calib"    => hc.calibrate()
              case "tempdiff" => if (params.nonEmpty) {
                val v = Math.round(params.head.toDouble).toInt
                hc.tempDiff(v)
              }
              case "settriggerstep" => if (params.nonEmpty) {
                val v = params.head.toInt
                hc.setTriggerStep(v)
              }
              case "sethalfdegreestep" => if (params.nonEmpty) {
                val v = params.head.toInt
                hc.setHalfDegreeStep(v)
              }
              case "setmintemp" => if (params.nonEmpty) {
                val v = params.head.toInt
                hc.setMinTemp(v)
              }
              case "setmaxtemp" => if (params.nonEmpty) {
                val v = params.head.toInt
                hc.setMaxTemp(v)
              }
              case "setrecenttemp" => if (params.nonEmpty) {
                val v = params.head.toInt
                hc.setRecentTemp(v)
              }
              case "setbuffersize" => if (params.nonEmpty) {
                val v = params.head.toInt
                hc.setBufferSize(v)
              }
              case other =>
            }
            case None => // ignore
          }
          publishState()
        }
      } catch {
        case t: Throwable =>
          log.error(t.getMessage, t)
          log.error("Continue ...")
          lastErrorBuffer.add(Error(s"WARNING (on setCallback will be continued) : ${t.getMessage}", DateTime.now))
//          newErrorSinceLastPublish.set(true)
      }
      override def deliveryComplete(token: IMqttDeliveryToken): Unit = log.info("delivery complete")
    })
  }

  /*
  def handleReconnectError(e: Throwable): Unit = {
    log.error(e, e.getMessage)
    if(clientOpt.isDefined){
      lastErrorBuffer.add(Error(s"ERROR (trying to reconnect) : ${e.getMessage}", DateTime.now))
      newErrorSinceLastPublish.set(true)
      disconnect(clientOpt.get._1, clientOpt.get._2)
    }
    log.info("Trying to reconnect ...")
    clientOpt = Some(connect())
  }
   */
}

