#!/bin/bash

if [ "$USER" == "root" ]; then
        if [ ! -f /root/.config/pva.root.overwrite ]; then
                echo "please, do not run this as root, it does only work for desktopsessions!"
                exit;
        fi
fi

cd /usr/share/pva/

DATE=$(date -R)

echo "PVA starting $DATE" >> $HOME/.var/log/pva.log

if [ ! -e $HOME/.config/pva ]; then

        mkdir -p $HOME/.config/pva/conf.d

fi

if [ ! -e $HOME/.cache/pva ]; then

        mkdir -p $HOME/.cache/pva/audio

fi

if [ ! -f $HOME/.config/pva/conf.d/02-paths.conf ]; then

        echo "path:\"video\",\"$HOME/Videos\""    > $HOME/.config/pva/conf.d/02-paths.conf
        echo "path:\"pics\",\"$HOME/Bilder\""    >> $HOME/.config/pva/conf.d/02-paths.conf
        echo "path:\"music\",\"$HOME/Musik\""    >> $HOME/.config/pva/conf.d/02-paths.conf
        echo "path:\"docs\",\"$HOME/Dokumente\"" >> $HOME/.config/pva/conf.d/02-paths.conf
fi

if [ ! -e $HOME/.var/log ]; then

        mkdir -p $HOME/.var/log

fi

MLANG=$(grep "^conf:.*lang_short" /etc/pva/conf.d/* $HOME/.config/pva/conf.d/* $HOME/.config/pva/pva.conf | tail -n 1 | sed -e "s/^.*,//g" -e "s/\"//g")
MODEL=$(ls -d *-$MLANG-*);

GREETING=$(grep "^conf:.*greeting" /etc/pva/conf.d/* $HOME/.config/pva/conf.d/* | tail -n 1 | sed -e "s/^.*,//g" -e "s/\"//g")
if [ "$GREETING" != "" ]; then
        SAY=$(grep say $HOME/.config/pva/pva.conf| sed -e "s/^.*,//g" -e "s/\"//g" -e "s/%VOICE/$MLANG/g" -e "s/x:x/ /g")

        if [ "$SAY" == "" ]; then
                SAY="/usr/local/sbin/say"
        fi
        $SAY "$GREETING"
fi

./pva.py -m $MODEL >> $HOME/.var/log/pva.log
