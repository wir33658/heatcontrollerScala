package de.robs.heatcontroller

import java.{lang, util}
import java.util.concurrent.{Callable, Future, TimeUnit}

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.DateTime
import com.pi4j.io.gpio.event.GpioPinListener
import com.pi4j.io.gpio.{GpioFactory, GpioPinDigitalOutput, GpioPinShutdown, GpioProvider, Pin, PinMode, PinPullResistance, PinState, RaspiPin}
import com.typesafe.scalalogging.Logger
import de.robs.CircularBuffer

object HeatController extends App {
  implicit val ac: ActorSystem = ActorSystem("heatcontroller")
  implicit val log = Logger("HeatController")

  val hc = new HeatController(true)

  for(i <- args.indices)println(s"arg($i): ${args(i)}")

  val cmd = if(args.length > 0)args(0) else "none"

  cmd match {
    case "calib"    => hc.calibrate()
    case "tempdiff" => hc.tempDiff(args(1).toInt)
    case unknown    => log.warn(s"Unknown command : $unknown!")
  }
}

case class Log(lastCommand: String, recentTemp: Double, triggerStep: Int, halfDegreeStep: Int, minTemp: Int, maxTemp: Int, time: DateTime)

class HeatController(sim: Boolean = false)(implicit ac: ActorSystem, log: Logger) {
  var TRIGGER_STEP = 100
  var HALF_DEGREE_STEP = 44
  var MIN_TEMP = 18
  var MAX_TEMP = 23

  val cb = new CircularBuffer[Log](50)

  val gpio7Out = if(sim)new SimGpioPinDigitalOutput(RaspiPin.GPIO_07, PinState.LOW) else GpioFactory.getInstance().provisionDigitalOutputPin(RaspiPin.GPIO_07, PinState.LOW)   // A
  val gpio0Out = if(sim)new SimGpioPinDigitalOutput(RaspiPin.GPIO_00, PinState.LOW) else GpioFactory.getInstance().provisionDigitalOutputPin(RaspiPin.GPIO_00, PinState.LOW)  // B
  val gpio2Out = if(sim)new SimGpioPinDigitalOutput(RaspiPin.GPIO_02, PinState.LOW) else GpioFactory.getInstance().provisionDigitalOutputPin(RaspiPin.GPIO_02, PinState.LOW)  // C
  val gpio3Out = if(sim)new SimGpioPinDigitalOutput(RaspiPin.GPIO_03, PinState.LOW) else GpioFactory.getInstance().provisionDigitalOutputPin(RaspiPin.GPIO_03, PinState.LOW)  // D

  val fullCircle = 510.0

  var counter = 0
  val lb = new StringBuilder

  var RECENT_TEMP = 20
  var lastCmd: String = ""

  gpioSetup(0,0,0,0)

  def dbLog(): Unit = {
//    cb.add(Log(lastCmd, RECENT_TEMP, TRIGGER_STEP, HALF_DEGREE_STEP, MIN_TEMP, MAX_TEMP, DateTime.now))
  }

  def setTriggerStep(value: Int): Unit = {
    TRIGGER_STEP = value
    log.info(s"Set trigger step to $TRIGGER_STEP")
    lastCmd = "SetTriggerStep"
    dbLog()
  }
  def setHalfDegreeStep(value: Int): Unit = {
    HALF_DEGREE_STEP = value
    log.info(s"Set half degree step to $HALF_DEGREE_STEP")
    lastCmd = "SetHalfDegreeStep"
    dbLog()
  }
  def setMinTemp(value: Int): Unit = {
    MIN_TEMP = value
    log.info(s"Set min temp to $MIN_TEMP")
    lastCmd = "SetMinTemp"
    dbLog()
  }
  def setMaxTemp(value: Int): Unit = {
    MAX_TEMP = value
    log.info(s"Set max temp to $MAX_TEMP")
    lastCmd = "SetMaxTemp"
    dbLog()
  }
  def setRecentTemp(value: Int): Unit = {
    RECENT_TEMP = value
    log.info(s"Set recent temp to $RECENT_TEMP")
  }
  def setBufferSize(value: Int): Unit = {
    val bs = if(value < 10 || value > 100)10 else value
    log.info(s"Set buffer size to $bs")
    lastCmd = "SetBufferSize"
    cb.maxSize = bs
    dbLog()
  }

  def toPinState(s: Int): PinState = if(s == 0)PinState.LOW else PinState.HIGH
  def toDegree(deg: Double): Double = fullCircle / 360 * deg

  def gpioSetup(a: Int,b: Int,c: Int,d: Int): Unit = {
    val ap = toPinState(a)
    val bp = toPinState(b)
    val cp = toPinState(c)
    val dp = toPinState(d)
    gpio7Out.setState(ap)
    gpio0Out.setState(bp)
    gpio2Out.setState(cp)
    gpio3Out.setState(dp)
    if(counter > 200){
//      log.info(lb.toString)
      lb.clear()
      counter = 0
    } else {
//      lb.append(s";Gpio-Setup : $a($ap), $b($bp), $c($cp), $d($dp)")
      counter = counter + 1
    }
   Thread.sleep(1)
  }

  def rightTurn(deg: Float): Unit = {
    log.info(s"Right-Turn : $deg")
    var degree = toDegree(deg)
    gpioSetup(0, 0, 0, 0)

    while(degree > 0.0) {
//      log.info(s"degree : $degree")
      gpioSetup(1, 0, 0, 0)
      gpioSetup(1, 1, 0, 0)
      gpioSetup(0, 1, 0, 0)
      gpioSetup(0, 1, 1, 0)
      gpioSetup(0, 0, 1, 0)
      gpioSetup(0, 0, 1, 1)
      gpioSetup(0, 0, 0, 1)
      gpioSetup(1, 0, 0, 1)
      degree -= 1
    }
  }

  def leftTurn(deg: Float): Unit = {
    log.info(s"Left-Turn : $deg")
    var degree = toDegree(deg)
    gpioSetup(0, 0, 0, 0)

    while(degree > 0.0) {
//      log.info(s"degree : $degree")
      gpioSetup(1, 0, 0, 1)
      gpioSetup(0, 0, 0, 1)
      gpioSetup(0, 0, 1, 1)
      gpioSetup(0, 0, 1, 0)
      gpioSetup(0, 1, 1, 0)
      gpioSetup(0, 1, 0, 0)
      gpioSetup(1, 1, 0, 0)
      gpioSetup(1, 0, 0, 0)
      degree -= 1
    }
  }

  def calibrate(): Unit = {
    log.info("Calibration started ...")
    leftTurn(TRIGGER_STEP)
    Thread.sleep(2000)
    leftTurn(40 * HALF_DEGREE_STEP) // should be max(= 30) now
    Thread.sleep(10000) // Back to 20 degrees
    rightTurn(TRIGGER_STEP)
    Thread.sleep(2000)
    rightTurn(20 * HALF_DEGREE_STEP) // should be 20 now
    gpioSetup(0, 0, 0, 0)
    setRecentTemp(20)
    Thread.sleep(2000)
    log.info("Calibration done.")
    log.info(s"Set temp should be $RECENT_TEMP")
    lastCmd = "Calibrate"
    dbLog()
  }

  def tempDiff(tempdiff: Int): Unit = {
    log.info(s"Tempdiff($tempdiff)")
    log.info(s"Recenttemp = $RECENT_TEMP")

    var goal = RECENT_TEMP + tempdiff
    log.info(s"goal = $goal")
    if(goal < MIN_TEMP)goal = MIN_TEMP
    else if(goal > MAX_TEMP)goal = MAX_TEMP
    else log.info(s"goal = $goal")

    val finaltempdiff = goal - RECENT_TEMP
    val finaltempdiffabs = Math.abs(finaltempdiff)
    log.info(s"finaltempdiff = $finaltempdiff")
    log.info(s"abs = $finaltempdiffabs")

    if(finaltempdiff < 0) {
      rightTurn(TRIGGER_STEP)
      Thread.sleep(2000)
      rightTurn(finaltempdiffabs * 2 * HALF_DEGREE_STEP)
      Thread.sleep(2000)
      setRecentTemp(goal)
    } else if(finaltempdiff > 0) {
      leftTurn(TRIGGER_STEP)
      Thread.sleep(2000)
      leftTurn(finaltempdiffabs * 2 * HALF_DEGREE_STEP)
      Thread.sleep(2000)
      setRecentTemp(goal)
    }
    gpioSetup(0, 0, 0, 0)

    log.info("Adjusted")
    log.info(s"Set temp should be $RECENT_TEMP")
    lastCmd = s"TempDiff: $tempdiff"
    dbLog()
  }
}

class SimGpioPinDigitalOutput(val pin: Pin, val defaultState: PinState)(implicit log: Logger) extends GpioPinDigitalOutput {
  var state: PinState = PinState.LOW
  var counter: Long = 0
  val logbuf = new StringBuilder

  override def high(): Unit = ???
  override def low(): Unit = ???
  override def toggle(): Unit = if(state == PinState.LOW)state = PinState.HIGH else state = PinState.LOW
  override def blink(delay: Long): Future[_] = ???
  override def blink(delay: Long, timeUnit: TimeUnit): Future[_] = ???
  override def blink(delay: Long, blinkState: PinState): Future[_] = ???
  override def blink(delay: Long, blinkState: PinState, timeUnit: TimeUnit): Future[_] = ???
  override def blink(delay: Long, duration: Long): Future[_] = ???
  override def blink(delay: Long, duration: Long, timeUnit: TimeUnit): Future[_] = ???
  override def blink(delay: Long, duration: Long, blinkState: PinState): Future[_] = ???
  override def blink(delay: Long, duration: Long, blinkState: PinState, timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long): Future[_] = ???
  override def pulse(duration: Long, timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long, callback: Callable[Void]): Future[_] = ???
  override def pulse(duration: Long, callback: Callable[Void], timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long, blocking: Boolean): Future[_] = ???
  override def pulse(duration: Long, blocking: Boolean, timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long, blocking: Boolean, callback: Callable[Void]): Future[_] = ???
  override def pulse(duration: Long, blocking: Boolean, callback: Callable[Void], timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState, timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState, callback: Callable[Void]): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState, callback: Callable[Void], timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState, blocking: Boolean): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState, blocking: Boolean, timeUnit: TimeUnit): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState, blocking: Boolean, callback: Callable[Void]): Future[_] = ???
  override def pulse(duration: Long, pulseState: PinState, blocking: Boolean, callback: Callable[Void], timeUnit: TimeUnit): Future[_] = ???
  override def setState(state: PinState): Unit = {
    if(counter > 100){
//      log.debug(logbuf.toString())
      logbuf.clear()
      counter = 0
    } else {
      counter = counter + 1
//      logbuf.append(s";$pin($state)")
    }
  }
  override def setState(state: Boolean): Unit = ???
  override def isHigh: Boolean = ???
  override def isLow: Boolean = ???
  override def getState: PinState = state
  override def isState(state: PinState): Boolean = ???
  override def getProvider: GpioProvider = ???
  override def getPin: Pin = pin
  override def setName(name: String): Unit = ???
  override def getName: String = ???
  override def setTag(tag: Any): Unit = ???
  override def getTag: AnyRef = ???
  override def setProperty(key: String, value: String): Unit = ???
  override def hasProperty(key: String): Boolean = ???
  override def getProperty(key: String): String = ???
  override def getProperty(key: String, defaultValue: String): String = ???
  override def getProperties: util.Map[String, String] = ???
  override def removeProperty(key: String): Unit = ???
  override def clearProperties(): Unit = ???
  override def export(mode: PinMode): Unit = ???
  override def export(mode: PinMode, defaultState: PinState): Unit = ???
  override def unexport(): Unit = ???
  override def isExported: Boolean = ???
  override def setMode(mode: PinMode): Unit = ???
  override def getMode: PinMode = ???
  override def isMode(mode: PinMode): Boolean = ???
  override def setPullResistance(resistance: PinPullResistance): Unit = ???
  override def getPullResistance: PinPullResistance = ???
  override def isPullResistance(resistance: PinPullResistance): Boolean = ???
  override def getListeners: util.Collection[GpioPinListener] = ???
  override def addListener(listener: GpioPinListener*): Unit = ???
  override def addListener(listeners: util.List[_ <: GpioPinListener]): Unit = ???
  override def hasListener(listener: GpioPinListener*): Boolean = ???
  override def removeListener(listener: GpioPinListener*): Unit = ???
  override def removeListener(listeners: util.List[_ <: GpioPinListener]): Unit = ???
  override def removeAllListeners(): Unit = ???
  override def getShutdownOptions: GpioPinShutdown = ???
  override def setShutdownOptions(options: GpioPinShutdown): Unit = ???
  override def setShutdownOptions(unexport: lang.Boolean): Unit = ???
  override def setShutdownOptions(unexport: lang.Boolean, state: PinState): Unit = ???
  override def setShutdownOptions(unexport: lang.Boolean, state: PinState, resistance: PinPullResistance): Unit = ???
  override def setShutdownOptions(unexport: lang.Boolean, state: PinState, resistance: PinPullResistance, mode: PinMode): Unit = ???
}