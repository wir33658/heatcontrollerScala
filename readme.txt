On PC:
sbt assembly
sbt "run <HOST> <PORT>"

On Pi:
cd projects/pi4ledScala/done
pi@raspberrypi:~/projects/pi4ledScala/done $
sudo java -jar pi4ledscala-assembly-0.1.0-SNAPSHOT.jar 192.168.178.52 4711 true

call :
http://192.168.178.52:4711/light
http://192.168.178.52:4711/switch


sudo shutdown -h now
