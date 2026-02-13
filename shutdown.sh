#!/bin/bash

if [ "$USER" == "root" ]; then
	if [ ! -f /root/.config/pva.root.overwrite ]; then
		echo "please, do not run this as root, it does only work for desktopsessions!"
		exit;
	fi
fi

cd /usr/share/pva/

PORT=$(LANG=C netstat -lna | grep -c -E ":39999.*LISTEN")
if [ "$PORT" == "1" ]; then

	GREETING=$(grep "^conf:.*shutdown" /etc/pva/conf.d/* $HOME/.config/pva/conf.d/* | tail -n 1 | sed -e "s/^.*,//g" -e "s/\"//g")
	if [ "$GREETING" != "" ]; then
	        SAY=$(grep say $HOME/.config/pva/pva.conf| sed -e "s/^.*,//g" -e "s/\"//g" -e "s/%VOICE/$MLANG/g" -e "s/x:x/ /g")

	        if [ "$SAY" == "" ]; then
	                SAY="/usr/local/sbin/say"
        	fi
	        $SAY "$GREETING" &
	fi

	NAME=$(grep "^conf:.*keyword" /etc/pva/conf.d/* $HOME/.config/pva/conf.d/* | tail -n 1 | sed -e "s/^.*,//g" -e "s/\"//g")
	if [ "$NAME" == "" ]; then
		NAME="Carola"
	fi
	
	EXIT=$(grep -E "command:.*\"AUTOEXIT\"" /etc/pva/conf.d/* $HOME/.config/pva/conf.d/*| tail -n 1| sed -e "s/^.*://g" | awk -F "," '{print $1;}'| sed -e "s/\"//g" -r -e "s/\\|/ /g")

	echo -n "$NAME $EXIT" | openssl s_client -connect 127.0.0.1:39999 -nbio 1>/dev/null 2>/dev/null


	CODE=$(grep -E "code:\"alpha\"" $HOME/.config/pva/conf.d/* | tail -n 1| sed -e "s/^.*://g" | awk -F "," '{print $2;}'| sed -e "s/\"//g" -r -e "s/\\|/ /g")


	CMD=$(grep -E "command:.*\"SHUTDOWNAUTHORIZE\"" /etc/pva/conf.d/* $HOME/.config/pva/conf.d/*| tail -n 1| sed -e "s/^.*://g" | awk -F "," '{print $1;}'| sed -e "s/\"//g" -r -e "s/\\|/ /g")

	echo -n "$NAME $CMD $CODE" | openssl s_client -connect 127.0.0.1:39999 -nbio 1>/dev/null 2>/dev/null

else 

	// this should not be happening

	PID=$(/usr/bin/pgrep -u $USER -f server.PVA)

	kill -9 $PID

fi


# PVA will shutdown now , which will call Plugin-Shutdown as well.

