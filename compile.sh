#!/bin/bash

cd /usr/share/pva

CP=""
for file in lib/*;do  CP="$CP:./$file"; done 

javac --release 11 server/PVA.java */*.java */*/*java -cp "$CP:."

# if you need Java 8 (legacy) change 11 back to 8

