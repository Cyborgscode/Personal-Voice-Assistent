#!/bin/bash

cd /usr/share/pva

CP=""
for file in lib/*;do  CP="$CP:./$file"; done 
for file in lib/*/*;do  CP="$CP:./$file"; done
java -cp "$CP:." server.PVA "startserver"
