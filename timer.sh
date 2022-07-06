#!/bin/bash

# This script is called ponce per minute by the systemd.timer, we create on first launch of PVA.
# it will just load the timer db and check, if a reminder should be presented (accustically)

cd /usr/share/pva
CP=""
for file in lib/*;do  CP="$CP:./$file"; done 
java -cp "$CP:." PVA
