#!/bin/bash

cd /usr/share/pva/

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

./pva.py 
