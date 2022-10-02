package de.robs.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.stream.Materializer
import de.robs.heatcontroller.HeatController
// import de.robs.mqtt.MqttSub
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.Logger

//class HttpRoutes(hostOpt: Option[String], portOpt: Option[Int], hc: HeatController, mqttSub: MqttSub)(implicit val system: ActorSystem, val materializer: Materializer, log: Logger) {
class HttpRoutes(hostOpt: Option[String], portOpt: Option[Int], hc: HeatController)(implicit val system: ActorSystem, val materializer: Materializer, log: Logger) {
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val host = hostOpt.getOrElse("192.168.178.52")
  val port = portOpt.getOrElse(8080)

  val route =
    path("home" / "heatcontroller" / "state") {
      get {
        /*
        val errors = mqttSub.lastErrorBuffer.ls
        val sb = new StringBuilder
        errors.foreach{error =>
          sb.append(s"<br>$error")
        }
        val errorStr = sb.toString()
         */

        val sb2 = new StringBuilder
        hc.cb.ls.foreach{ log =>
          val v1 = log.time.toIsoDateTimeString()
          val v2 = log.time.clicks
          val v3 = log.lastCommand
          val v4 = log.recentTemp
          val v5 = log.minTemp.toString
          val v6 = log.maxTemp.toString
          val v7 = log.halfDegreeStep.toString
          val v8 = log.triggerStep.toString
          val str = s"<br>time: $v1; longTime: $v2; lastCommand: $v3; recentTemp: $v4"
          sb2.append(str)
        }
        val str = sb2.toString()

//        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>Errors : </h1>$errorStr\n<h1>States : </h1>$str"))
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<h1>States : </h1>$str"))
      }
    }

//  val bindingFuture = Http().bindAndHandle(route, host, port)
  val bindingFuture = Http().newServerAt(host, port).bind(route)   // .bindAndHandle(route, host, port)

/*
  println(s"Server online at http://$host:$port/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
 */

  def unbind(): Unit ={
    bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }
}

