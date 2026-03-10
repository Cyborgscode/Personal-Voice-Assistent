#!/bin/bash

cd /usr/share/pva

CP="/usr/lib/java/jna.jar:/usr/share/java/jakarta-activation1/jakarta.activation.jar:/usr/share/java/jakarta-mail1/jakarta.mail.jar"
for file in lib/*;do  CP="$CP:./$file"; done 
for file in lib/*/*;do  CP="$CP:./$file"; done
ADD=""

if [ -e plugins/files/WatchdogPlugin.java ]; then

	CP="/usr/lib/java/opencv.jar:$CP"
	ADD="-Djava.library.path=/usr/lib/java/"
	LD_LIBRARY_PATH=/usr/lib/java:/usr/lib64:$LD_LIBRARY_PATH
fi

java  $ADD -cp "$CP:." server.PVA "startserver"  >> $HOME/.var/log/pva.log 2>> $HOME/.var/log/pva.log
