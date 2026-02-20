#!/bin/bash

if [ "$1" == "" ]; then
   cd /usr/share/pva
else
   cd "$1"
fi

if [ "$2" == "clean" ]; then
	shift 1
	find . -iname "*.class" -delete
fi	

if [ "$2" == "-withcustomlibs" ]; then
   CP="$3"
else 
   if [ "$2" == "-withjnapackaged" ]; then
      CP=""
   else 
      CP="/usr/lib/java/jna.jar"
   fi
fi

# We use our own HTTP Class, which got invented years before java implemented theire own, so we need to focus on our own class files, in case you wonder why this looks unnecessary. It isn't ;)
# It compiles fines with Java 21. The "--release 11" is just backwards compatibility for older javas. You are free to remove it if you wanne a newer Java

for file in lib/*jar;do  CP="$CP:./$file"; done 

echo "Classpath: $CP"

javac --release 11 server/PVA.java */*.java */*/*java -cp "$CP:."

# if you need Java 8 (legacy) change 11 back to 8

