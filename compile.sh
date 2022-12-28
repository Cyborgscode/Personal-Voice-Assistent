#!/bin/bash

cd /usr/share/pva

CP=""
for file in lib/*;do  CP="$CP:./$file"; done 

javac --release 8 server/PVA.java */*.java */*/*java -cp "$CP:."


