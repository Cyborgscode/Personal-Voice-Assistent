#!/bin/bash

/usr/bin/wmctrl -i -a $(/usr/bin/wmctrl -l|/usr/bin/grep Netflix|/usr/bin/sed -e "s/ .*//g")
