#!/bin/bash


GREETING=$(grep "^conf:.*shutdown" /etc/pva/conf.d/* $HOME/.config/pva/conf.d/* | tail -n 1 | sed -e "s/^.*,//g" -e "s/\"//g")
if [ "$GREETING" != "" ]; then
        SAY=$(grep say $HOME/.config/pva/pva.conf| sed -e "s/^.*,//g" -e "s/\"//g" -e "s/%VOICE/$MLANG/g" -e "s/x:x/ /g")

        if [ "$SAY" == "" ]; then
                SAY="/usr/local/sbin/say"
        fi
        $SAY "$GREETING"
fi


PID=$(/usr/bin/pgrep -f ./pva.py)

kill -9 $PID

PID=$(/usr/bin/pgrep -f server.PVA)

kill -9 $PID
