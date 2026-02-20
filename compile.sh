#!/bin/bash

if [ "$1" == "" ]; then
   cd /usr/share/pva
else
   cd "$1"
fi

if [ "$2" == "-withsystemjna" ]; then
   CP="/usr/lib/java/jna.jar"
else
   CP=""
fi

# We use our own HTTP Class, which got invented years before java implemented theire own, so we need to focus on our own class files, in case you wonder why this looks unnecessary. It isn't ;)
# It compiles fines with Java 21. The "--release 11" is just backwards compatibility for older javas. You are free to remove it if you wanne a newer Java


for file in lib/*jar;do  CP="$CP:./$file"; done 

javac --release 11 server/PVA.java */*.java */*/*java -cp "$CP:."

# if you need Java 8 (legacy) change 11 back to 8

