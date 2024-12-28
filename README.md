> /*
>
> PVA is coded by Marius Schwarz in 2021-2024
>
> with help of the contributors on GitHub.
>
> */


Hi,

"What is PVA" you may ask. PVA is an Open Source Voice Assistent for Linux/Unix, which runs 100% local if you install it.
As it's java and python based, you will find a lot of people who can supply code or do code audits and improvements.

PVA listens to a Keyword, which you can redefine on the fly if needed.
It's also not language depended, which means, YOU can modify the scripts and configs to reflect any language VOSK supports. See next chapter.

Feel free to adopt it to your perfect assistent or your best friend, as there is next to no limit. 

PVA is capable of reading emails, making calls, answering phone calls, plays music & videos, searches for documents and media files, does a personal TOP list of favorite music ( requires genre metadata in your mp3 files -> Picard is your friend ), survails your pc for you or writes email if needed.

Nothing of this requires any AI. 

If used in default mode, it won't rat your personal data out to a company, except for the weatherreport, which you can disable.

**On what it depends** 


This project depends on the AlphaCephei Software VOSK, which can be found here:

https://alphacephei.com/vosk/

Language models for 18 different languages can be found here: 

https://alphacephei.com/vosk/models

To run it, you need at least:

```
python3
pip3
portaudio
mbrola             (found on github)
espeak
vosk               (found on github)
sox                (needed in case you want to make use of GTTS)
openssl            (used to communicate with the PVA TLS server / works with 1.x and 3.x)
dbus-tools
```

Optional dependencies:

```
ffmpeg
fswebcam
gnome-screenshot
```


# JAVA deps have been removed to prepare for distro packaging

### How to install:

Via RPM REPO:

create /etc/yum.repos.d/pva with this content:

```
[pva]
name=PVA $releasever - $basearch
baseurl=http://repo.linux-am-dienstag.de:80/$basearch/fedora/$releasever/
enabled=1
metadata_expire=1d
#repo_gpgcheck=1
type=rpm
gpgcheck=1
gpgkey=http://repo.linux-am-dienstag.de:80/RPM-GPG-KEY-fedora-$releasever-$basearch
```

Execute the following commands as root:

```
dnf makecache --repo=pva
dnf install pva-base pva-vosk-model-de-small
```

for german speaking people: that's it. 
All non german users need to:
1. Install a different language model
2. Translate the configs to their language first, otherwise it won't work.

Manual installation via Github:

We don't need fancy frameworks or complicated makefiles, so relax, it's just done in about 5 minutes :) The author uses Fedora as OS,
so if you run Ubuntu or Arch, your install commands will vary a bit.

## if you want to use GTTS instead of espeak/mbrola:

You need to checkout gsay, change your config to use gsay as "say" app and install gtts for your distro:

i.e. Fedora: `dnf install gtts`

#gsay caches created phrases to minimize the contact to Googleservers, but at least once per sentence you speak, it will need to contact it. 

## Piper TTS 

Source: https://github.com/rhasspy/piper

Support for piper TTS has been added. You need the following directory structure:

```
/usr/local/share/piper
├── espeak-ng-data
└── piper

/usr/local/share/piper-voices
├── voice-de-eva_k-x-low
│   ├── de-eva_k-x-low.onnx
│   ├── de-eva_k-x-low.onnx.json
│   └── MODEL_CARD
...
├── voice-de-thorsten-low
│   ├── de-thorsten-low.onnx
│   ├── de-thorsten-low.onnx.json
│   └── MODEL_CARD
...
```

Piper TTS is 100% local, so we have no privacy issues. 

## WARNING: 
### Your personal privacy is at risk if you use gtts, as the text of the PVA answers (which includes repeats of what you said) is transferred to a Google server, for which you need an active internet connection too. 

## Mimic3

You can use mimic3, a local TTS system created by the MyCroft team. After installing it, you just need to add:

alternatives:"mimic sprachausgabe","say","/usr/bin/mimic3x:x--voicex:x%VOICE"

and than tell pva to use this alternative via the voice command. 

If that does not work, if your pva config language differes from the scheme used by mycroft, just create a small script as /usr/local/sbin/mimic3say 

```
#!/bin/bash

if [ "$VOICE" == "de" ]; then
	VOICE="de_DE"
fi

... your block if-condition for language X...

/usr/bin/mimic3 --voice $VOICE
```
use this:

alternatives:"mimic sprachausgabe","say","envx:xVOICE=%VOICEx:x/usr/local/sbin/mimic3say"

make sure, your voice package is installed and has the correct checksums. 

## Espeak:

```sudo dnf -y install espeak```

## Mbrola: ( OPTIONAL, but than you have to live with espeak ;) ) 

The say script now checks if mbrola is available. if it's not, it uses native espeak which sounds terrible ;)

Download:

https://github.com/numediart/MBROLA
https://github.com/numediart/MBROLA-voices

Compile: ( needs gcc )

```
cd MBROLA-master
make
cp Bin/mbrola /usr/local/sbin/
```

Experiment a bit with the best voice for you. You will find the `say` Bash script in this repo, but it will default to de5, as it's setuped for german.
Just replace de5 in "say" with your prefered language and don't forget to translate the responses in /ect/pva/conf.d/* ;)

If you need help with mbrola: https://marius.bloggt-in-braunschweig.de/2021/03/24/mbrola-etwas-bessere-sprachsynthese/

## F5-TTS support 

PVA does now support a way to use a local F5-TTS speechserver, which creates soundoutput with a voice of your choice, no cloud service required.
You need tons of python files and a massive amount of datafiles ( app. 5 GB ), that get installed  by the installer, but the results
are unbelieveable in some situations. If you wanne have PVA answere your requests with the voice of the Terminator, than sample 10s of his voice
and transcribe the text into a txt file .. that's it.

German speakers need a special sample set, more in the f5tts scripts, to make it work. The sample set has some bugs, so the creation of very short sentences has some issues atm. The english ones work way better.

No additional commands are required, because it's just one new voiceoutput of many already integrated. You can use "benutze ... sprachausgabe". Check file "14-sprachausgaben.conf" for more.


## Python3 + Pip3 + Portaudio:

```pip3 install sounddevice```

ATTN: do not install it as user, if you wanne install it systemwide ( see below on "Installation" )

```sudo dnf install python3-pyaudio espeak```

## Vosk:

On your desktop you can directly install vosk :

```pip3 install vosk```

ATTN: do not install it as user, if you wanna install it systewide ( see below on "Installation" )

If you wanna run this on a Pinephone, as recently demonstrated, you need atleast this BETA version:

```pip3 install https://github.com/alphacep/vosk-api/releases/download/v0.3.30/vosk-0.3.30-py3-none-linux_aarch64.whl```

check if there is a newer version, before you run this command.

Demonstration: http://static.bloggt-in-braunschweig.de/Pinephone-PVA-TEST1.mp4

## Getting the right language model:

Download them here:

https://alphacephei.com/vosk/models

There are small models, with a smaller stock of words, but it may already be enough for your tasks, or you can use the big ones ( ~1.9 GB ), which have a lot more words, but tend to have false positives for exactly this reason :) i.e. in german "füge" and "flüge" sound very similar and this gives us a relativly high error rate. You should use the small model on the Pinephone, due to it's size advantage.

####
ATTN: previouse installation procedures focused on a private, one user only installation. We now focus on a MULTI-USER systemwide installation.
####

PVA is now fully aware of freedesktop directorystructures and makes use of it.

## Installation: 

(replace the modelfile with your filename! )

Become root, sudo alone is not enough!

use `sudo -i` or `sudo su` or just `su` if you still have a root password.

```pip3 install sounddevice vosk```

# NOTE: If you wanna do development work on this, your repo should be located somewhere else, as you will move files out of the repo directories and git won't like that ;)

# Example if you i.e. used a zip archive of pva

```
mkdir -p /usr/share/pva
cd /usr/share/pva
copy the relevant zip content here (means, do not include "master" or something similar in the path)
mv etc/pva /etc/
```

# NOTE: You don't need to "move" files into place, you can of course copy them i.e. with `cp` or `tar c etc | tar x -C /`

Example on the modelfile:

```
unzip vosk-model-small-de-0.21.zip
ln -s vosk-model-small-de-0.21 model

mkdir -p /usr/local/sbin
cp usr/local/sbin/*say /usr/local/sbin 
```

# NOTE: alternative: `tar c usr |tar x -C /`

now compile the JAVA Classes ( requires javac aka openjdk-devel packages ) :

```
/usr/share/pva/compile.sh
```

move desktop/* to /usr/share/applications/

You should now find two PVA entries in your desktop app menu.

You can now use your desktop specific autostarttool ( Startprogramme ) to add PVA to your desktop autostart.

You should hear the default ( german ) startup greeting, next time you start your desktop session.
Of course, you can start PVA the first time manually from the app menu ;)

Check the processlist if a process "python3" appeared for pva.py. If not, the startup did not work, something is wrong.

In this case:

switch to your desktop userid

```
cd /usr/share/pva
./pva 
```

If you did not install the german model file, you need to change the /etc/pva/conf.d/01-default.conf to your model name, by overwriting the config in a user.conf file:

the directory "~/.config/pva/conf.d/" should already exists, as you had started PVA via your app menu. The executed "pva" bash script will create all necessary directory and basic config files for you.

In case it does not exist, the PVA start failed much earlier in the chain and you need to check the desktop files in /usr/share/applications/ for more.

To overwrite the config you create this:

```
mkdir -p ~/.config/pva/conf.d/
echo 'vosk:"model","vosk-model-de-0.21"' > ~/.config/pva/conf.d/00-model-overwrite.conf
```

Of course you have to use the model name you need, not this example ;) 

Don't forget to update it everytime you update the model version. 

## Why is it in the config file and not defaulting to the symlink "model" from above?

Because PVA can switch the model on a voice command :) It will be changed on the next app restart.

PVA will search for a matching language model from the list of models located in /usr/share/pva.
To add a model, just download and unpack it there.

Example:

```
$ ls -l /usr/share/pva/
insgesamt 244
-rw-r--r--. 1 root root   478 26. Jan 10:50  AppResult.class
drwxrwxr-x. 3 root root  4096 30. Dez 2020   com
-rw-rw-r--. 1 root root   480 26. Jan 10:50  Command.class
drwxr-xr-x. 2 root root  4096 17. Jul 2021   data
drwxrwxr-x. 2 root root  4096 16. Dez 18:58  hash
drwxrwxr-x. 2 root root  4096  4. Dez 17:41  io
lrwxrwxrwx. 1 root root    18 20. Dez 17:44  model -> vosk-model-de-0.21
-rwxr-xr-x. 1 root root  2246 26. Jan 11:18  pva
-rw-r--r--. 1 root root  1977 26. Jan 10:50 'PVA$AnalyseMP3.class'
-rw-rw-r--. 1 root root 55337 26. Jan 10:50  PVA.class
-rw-r--r--. 1 root root 14214 20. Dez 17:27  pva.conf.default
-rw-r--r--. 1 root root 99253 26. Jan 10:50  PVA.java
-rwxrwxr-x. 1 root root  2512 20. Dez 21:55  pva.py
-rwxr-xr-x. 1 root root   970 20. Jan 18:42  pvatrayicon.py
-rw-rw-r--. 1 root root   421 26. Jan 10:50  Reaction.class
-rw-rw-r--. 1 root root  4963 23. Jul 2021   README.txt
-rwxr-xr-x. 1 root root   461 10. Jan 11:43  shutdown.sh
drwxr-xr-x. 2 root root  4096 26. Jan 11:17  systemd
-rwxr-xr-x. 1 root root    41 25. Jan 20:03  timer.sh
drwxr-xr-x. 8 root root  4096 15. Sep 00:21  vosk-model-de-0.21
drwxr-xr-x. 6 root root  4096  8. Dez 2020   vosk-model-small-en-us-0.15
```

it does detect small and large model files, but don't use both per language.


<== Carola, a PVA ==>


Carola is the keyword I use to address the pva. You can change this in an overwrite file in `~/.config/pva/conf.d/00-keyword-overwrite.conf` you need to create.

HINT:

In case your desired app does not have the same functionality as the default apps, which are the default apps for a reason, create bash wrapper scripts and use those.

SETUP:

You noticed the `pva.conf.default` file? It's now completely useless, but may give some impression on how to configure it.

To start, visit `/etc/pva/conf.d/`  and check the german numbers file. It's the equivilant to a `locale` file. It container numbers, monthnames and weekdaynames etc. Clone it as `10-<yourlanguage>-numbers.conf` and change the content accordingly. Most languages of the western world will match the format used. Numbers from 0-19 differently but from 20+ calculateable, 7 days a week etc. If you need to use a none romanic based language, PVA may needs to be rewritten and for this we surely need to your help with examples, rulesets etc. 

There are two ways to handle changes: 

You can do them for YOUR useraccount only by placing the configfiles into `~/.config/pva/conf.d/`  OR
You can write new configfiles with higher startnumbers in `/etc/pva/conf.d/` like `10-defaults-english.conf` and just rewrite and slightly adapt the texts used in the german default file. 

The higher number ( 10 ) means, this configfile is read later, which means, it's content is overwriting stuff the is unique. 

```
text:"de","KEYWORD","meine text version in deutsch"
command:"erzeuge|metadata","MAKEMETACACHE",""
```

Of course you do not overwrite the german "text" base, instead you change it like this:

```
conf:"lang_short","en"
conf:"lang","en_EN"

text:"en","KEYWORD","your version in english"
command:"create|metadata","MAKEMETACACHE",""
```

The "command" line does not overwrite the german command, it adds it to the command hashtable. You ask why: because an english model won't recognize german words anyway and vice versa, and so we don't need to overwrite those texts. You can just add as may lines in as many languages as you like and give them the ACTIONKEYWORD it needs.

For the `text` section, you can't do this simple way, we need those texts distinguished per as tripple-pairs of Language+Keyword+Text. 

The `conf` entry overwrites the german default setting, as you can only have one language per user. 

If you don't have the apps  in there, just install them. 

You can find qmmp & celluloid in the fedora repos, but to fully make use of qmmp, you may wanne install some plugins from rpmfusion.org. 
Jitsi ( Desktop version ) can be found on github and leave a +1 to Ingo Bauersachs, the main dev of Jitsi, you will LOVE calling people just with your voice ;)
Jitsi will be the bridge to your SIP provider, which can be your dsl or cable modem or something like SIPgate, Asterix etc.

A online CardDAV addressbook (with content) is required for some functions like sending email or calling someone. If you find a way to get those infos from the desktop contact app, please send a pull request. CardDAV has the advantage of also working in Thunderbird, which gives you a nice UI to enter your data. Also Thunderbird is the main MailAPP.

The `pva.conf` file is self explaning.

## Since commit #2c74e1d (16.12.2021) you can have multiply config files. 

There are two places configs are found:

```
/etc/pva/conf.d/00-test.conf
~/.config/pva/conf.d/00-test.conf
```

The actual config `./pva.conf` is always loaded as the last config file, to overwrite existing, alternative settings . I.E. if you changed the searchengine via "use duckduckgo as searchengine" and you had google before, you need to save this up. The file is found in $HOME/.config/pva/ .

COMMANDS:

You will find here the commands, PVA can understand atm. You will see, they are in german, but explained in english.
Thats because, it has been developed in german. Now any language can be supported, but someone still needs to write those commands and responses in theire native language and post them. If you wannte help out, get the /etc/pva/conf.d/ files and move them from 0x-.. to 1x-... then translate them. It may be easier for you to do that at $HOME/.config/pva/conf.d/ , because you don't need to be root to do it.

Any time you see a "|" it seperates AND-connected keywords:

"you|succeed|will" can be any combination of those words, but they ALL need to be in the spoken sentence to have a positive match.
In case the cmd has an optional part like this:

command:"i|want|to|listen","PLAYAUDIO","add|it"

it matches: "I want to listen to queen "

it also matches: "I want to listen to queen add it" AND it removes "add" and "it" from the sentence, so those words do not end up in the searchterm.

it also matches: "I want to listen to queen it" and removes the "it", because it tries to still remove "add" and "it". 

You need this to remove words, that shall not part of the search term.

## More advanced commands:

There are two different ways to start playing music:  `PLAYAUDIO` and `PLAYMUSIC`

They do the same thing, but sligtly different:

`PLAYAUDIO` is a cmd to search for a song or artistname and adds all matches to a new playlist, afterwards the playback starts.
If you use the `PLAYAUDIO` keywords and ADD the `PLAYMUSICRADD` keywords, the playlist is not cleared and all matches will be added to the list.

`PLAYMUSIC` means, "start playback" whatever is in the list, from the actual position in the list. You can use this to restart a stopped playback. 
But PLAYMUSIC can do more, it can randomly search the database and play a song. If you use PLAYMUSIC + PLAYMUSICRANDOM + PLAYMUSICRADD together, 
it adds those randomly loaded songs to the end of the current playlist. 

You find those keywords defined in the textsection:

`text:"de","PLAYMUSICRANDOM",".*(zufällig).*"`
`text:"de","PLAYMUSICRADD",".*(noch|dazu).*"`

in english:

`text:"en","PLAYMUSICRANDOM",".*(random).*"`
`text:"en","PLAYMUSICRADD",".*(add).*"`

As you see, it's a Regular Expression, which is a mighty tool to match patterns in texts. If you know how to use them, you can have a lot of fun here,
BUT REMEMBER, all additional keywords need to be removed in the cmd section to they end up as parts of the searchterm!

There are some commands that have optional parameters. The easiest way to spot them is to check the source OR to search for the keyword in conjunction with the "text:" definition ( i.e. `PLAYMUSIC` -> `PLAYMUSICRANDOM` )

A third cmd, `ADDTITLES` adds a number of randomly selected songs to the playlist.


{TERM} mandatory infos needed to perform task
[TYPE] i.e. optional info i.e. number type like cellphone, work or home

Keyword-Sentences in Human Response Simulation:

"ha ha ha"
"danke"
"sehr witzig"
"das funtioniert"
"das funtioniert nicht"
"wie ist dein name"
"wie heißt du"

With the "Nightprotection" commit, you can configure a timeinterval when reactions are not played. It's usefull if you assistent runs 24h a day.

Keywords in Bot Reaction: " Keyword " + content

```
"autorisierung code {TERM}"		-> give secret code to ack previous command
"autorisierung neuer code {TERM} "	-> set new secret code 

"nochmal"                               -> repeat last cmdline

"wie war der letzte fehler" 		-> readout last error message of selfcompile try
"wie war die fehlermeldung" 		-> readout last error message of selfcompile try

"schalte dich ab" 			-> request Alpha code and exit
"compiliere dich neu" 			-> compile the PVA.java file new  ( This obviously can only work, if it's installed for your userid )

"liste telefonbuch auf" 		-> print out entries in carddav database (it's for debug) 

"rufe {TERM} [TYPE]  an"		-> search for TERM in carddav database and execute jitsi/calls to make the call 
"{TERM} [TYPE] anrufen" 		-> 
"ich möchte {TERM} [TYPE] sprechen"	-> 
"ich möchte mit {TERM} [TYPE] reden"	-> 

"mache einen screenshot"		-> make screenshot
"mache screenshot"

"wie spät ist es"			-> tells the actual time
"sag mir die Uhrzeit"

"wie hoch ist die last"			-> tells the actual /proc/loadavg
"wie hoch ist die load"

"wie ist das wetter"			-> gives out actual weather report for {LOCATION}
"wie wird das wetter"			-> gives out actual weather report for the next hours
"wie wird das wetter morgen"		-> gives out actual weather report for tomorrow

"beende {TERM}"				-> stops apps  {TERM} =>  firefox, wetter, karte, karten, kartenapp, film, video, musik, audio
"stoppe {TERM}"				   "film" "video" "musik" "audio" refer to teh configured players

"starte {TERM}				-> starts apps  {TERM} => "blender" "wetter" "karte" "karten" "kartenapp" "firefox" "netflix" "windy" "openoffice" "emailprogramm"
"öffne  {TERM}				# ALIAS of starte 
"öffne {TYPE} mit {APP}"		-> takes the last search result for {TYPE} and opens it with {TERM} . This implies, that you did a search before.
	
"ich möchte {TERM} hören"		-> search for musik files matching TERM, where TERM can have more than one word. on Success the audioplayer gets informed

"was ist da zu hören"			-> ask audioplay about trackinfos
"was ist gerazu zu hören"
"was spielst Du gerade"
"was höre ich"
      
"ich möchte {TERM} sehen" 		-> search for video files matching TERM, where TERM kann have more than one word. on Success the videoplayer is started

"schreibe email an {TERM} [betreff [subject text]] [inhalt [body content]]" -> opens mailapp (thunderbird) IF a emailaddress for TERM has been found. Optional: use keyword "inhalt" or "betreff" to add infos at the right positions.

"suche email {TERM}" 			-> searches for Emailaddress of TERM in carddav database and reads it.
"suche (telefon|telefonnummer) {TERM}" 	-> searches for phonenumber of TERM in carddav database and reads it, according to it's type.

"suche dokument {TERM}"			-> search on ~/Dokuments aka. ~/Documents  for TERM as PDF or TXT or ODT/S/P
"suche dokument {TERM} pdf"		-> search on ~/Dokuments aka. ~/Documents  for TERM as PDF

"suche bild {TERM}"			-> searches in configures pics paths for: JPEG,JPG,GIF,PNG,SVG

"lies pdf {TERM}" 			-> **EXPERIMENTAL** .. find and read out pdf file
"lies text {TERM}" 			-> find and read out txt files

"spiele musik" 				-> starts audioplayer in playback mode ( whatever it had in its queue )
"spiele musik zufällig" 		-> search the music database and randomly chooses one file
					   ** the entire audio database is saved to a cache file for fasten up requests ** 
"stoppe musik"				-> stops audioplayer playback
"halte musik an"
"musik anhalten"
"musik stop"
"stop musik"

"mach leiser"				-> decrease volume on audioplayer
"mache leiser"	

"mach lauter"				-> increase volume on audioplayer
"mache llauter"	

"musik leiser"				-> decreases musik volume by 1 steps
"musik viel leiser"			-> decreases musik volume by 5 steps

"musik lauter"				-> increases musik volume by 1 steps
"musik viel lauter"			-> increases musik volume by 5 steps

"nächster titel"			-> skip audio track by one
"nächstes stück"			-> skip audio track by one
"nächstes lied"				-> skip audio track by one

"übernächster titel"			-> skip audio track by two
"übernächstes stück"			-> skip audio track by two
"übernächstes lied"			-> skip audio track by two

"{NUMBER} lied weiter"			-> skip audio track by NUMBER : can be anything from 1 to 99
"{NUMBER} lieder weiter"		-> skip audio track by NUMBER : can be anything from 1 to 99

"{NUMBER} lied zurück"			-> skip audio track backwards by NUMBER : can be anything from 1 to 99
"{NUMBER} lieder zurück"		-> skip audio track backwards by NUMBER : can be anything from 1 to 99    

"springe {NUMBER} vorwärts"		-> advance by NUMBER of seconds : can be anything from 1 to 99
"springe {NUMBER} vor"			-> advance by NUMBER of seconds : can be anything from 1 to 99

"springe {NUMBER} zurück"		-> go back in track by NUMBER of seconds : can be anything from 1 to 99
"{NUMBER} lieder zurück"		-> go back in track by NUMBER of seconds : can be anything from 1 to 99    

"ton an"				-> unmute audioplayer
"ton aus" 				->   mute audioplayer

"benutze {TERM}"                        -> switches to named alternative for a command
"websuche {TERM}"                       -> starts a websearch based on configured searchengine and prefered browser.
"bildschirm sperren"                    -> locks the desktop
"bildschirm entsperren"                 -> locks the desktop

"video fullscreen"			-> tell media/videoplayer to go to fullscreen 
"video vollbild"			

"video fullscreen aus"			-> tell media/videoplayer to go back to windowed mode
"video vollbild aus"

"{NUMBER} videos weiter"		-> skip N videos in the playback queue forward
"{NUMBER} videos zurück"		-> skip N videos in the playback queue backwards
"ein video weiter"			-> skip 1 video forward
"nächstes video"

"ein video zurück"			-> skip 1 video backwards
"letztes|video"				

"video weiter"				-> UNPAUSE or start videoplayback, which in most mpris implementations does the same.
"video start"
"video|fortsetzen
"wiedergabe|starten
"wiedergabe|fortsetzen

"video stop"				-> stop videoplayback FULLY <- this is an important difference regarding PAUSE!
"wiedergabe|stop

"pausiere video"			-> pause videoplayback
"video pause"        
"wiedergabe|pausiere

"erzeuge metadata"
"erzeuge mp3 metadata"

"erinnere mich {DAY} um {TIME} an {TEXT}" 
					-> add a timed reminder to the database at {TIME} on {DAY} with the reminder {TEXT}
					DAY parameter is optional and defaults to "today"
					Example: "erinnere mich um neun uhr zwölf an Frühstück einnehmen mit Carola"
						 which result in an entry for TODAY 09:12 (AM) with the text "Frühstück einnehmen mit Carola"
					Example: "erinnere mich morgen um neun uhr zwölf an Frühstück einnehmen mit Carola"
						 which result in an entry for the next morning 09:12 (AM) with the text "Frühstück einnehmen mit Carola"
					Example: "erinnere mich freitag um neunzehn uhr fünfundfünfzig an romatisches Abendessen im Donbass mit Carola"
						 which result in an entry for next Friday 19:55 (PM) with the text "romatisches Abendessen im Donbass mit Carola"
						 
"ließ termine vor"			-> lists verbally all reminders
"meine termine"

"benenne dich in {NAME} um"		-> rename yourself to {NAME}
"dein neuer name ist {NAME}"
"dein neuer name lautet {NAME}"

! This part works only, if you add the twinkle scripts & configs to your main or user config directory. 
! The config and scripts need manual adjustment to your system to function proper! 
! But, you can archive cool stuff with the underlying command mechanics!

"schalte X auf Y um" 	-> switches pulseaudio output device for process X to sink Y
```


## How PVA detects your favorite music for a specific genre ?

In case a metadatacache IS ENABLED AND exists AND you had searched for i.e.  a pop song in the past AND you had a MP3 tag set with that genre in that specific popsong mp3 AND you tell PVA that "you wanne listen to pop", the new algorithm calculates your favorite pop song (if you have one) and adds it/them to the searchresult instead of searching to the term "pop" in your musik-cache.

This may get in the way of you searching a big database of soundtracks using the term "soundtrack" ;)

### Step by Step 

You will need a tagged mp3 library with genres set for this to work. I suggest to use "PICARD" for this task. 

If you tell PVA to "listen to XXX ", PVA searches your music.cache for a matching file.
If one or less that 25 are found in a search, they are added to the music.stats cachefile.

If you are in a moud and want to listen to pop, classic etc. etc. you tell PVA "i want to listen to pop".
While creating cache.metadata via the command MAKEMETACACHE, PVA can find mp3 files with genre data and these are stored and loaded on PVA startup, to have a valid list of genre names. Otherwise, PVA would not know what a genre is at all. 
So, if "pop" is a genre in your cache.metadata file, PVA will load the favorites ( music.stats ), calculates how often you searched for a (or more) specific files, sorts the result top down and checks this list against the cache.metadata informations for the required genre.
If one or more are found, they get added to the searchresult list and you will instantly hear them.

This results in a minor problem if you really wanne search for all songs containing "pop" in the name: you can't anymore. If you need this, disable the metacache favorite search by replacing "true" with "false". 

## Cluster Setup

With the Cluster Plugin, you can have satelite clients. Between the configured client, **which need to run pipewire-pulse & wireplumber** tcp-tunnels are created to cluster the speakers and microphones. So, yes, you are now able to roll out a bunch of raspis with USB soundbars or additional pcs in your home, and  play musik on all of them. There is no latency control, so do not put them too near together ;) Also you can give commands to your PVA via each of those microphones. 

SSH-SETUP:

You need to be able to access the clients via ssh. You can create keys for each client, or use a default key. It's up to you.

If you enter "ssh desktopuser@192.168.178.2" or "ssh -i ~/.ssh/key.for.ip.2.rsa desktopuser@192.168.178.2"  and you can login without a password,
your good to go. Key passwords are handled by your ssh-agent i.e. the gnome-keyring-daemon. Don't ask for password support, you won't get it. 

Features:

PVA-Cluster detects the presence or absence of configured clients automatically and setups or destroys the tunnel on the fly. You do not need to restart PVA to make this work. 

Two new sinks are available ALLTUNNEL and ALLMICS. All clients are auto-linked to these two sinks. ALLMICS is linked to the PVA Inputnode und ALLTUNNEL is connected to any outgoing tunnel speaker and the default speaker you configured. 

All you need to do is open PAVU Control or ANY OTHER Pipewire Alternative App i.e. QPWGRAPH and transfer the output of you app to the ALLTUNNEL sink.

PVA won't do this for you, as there are a lot of possible configurations imaginable that would be sabotaged by automatic linkage. I.E. if you run EASYEFFECTS and have taken control of your musicapp, you select ALLTUNNEL in the pipewire tab of EASYEFFECTs and enjoy it's benefits. If PVA would interfere here, you audiosetup could break a bit, and thats not PVAs intend. So, YOU choose, where your apps shall output its sound to, PVA just setups the cluster for you. 

"Computer restart client <NAME>" => restarts the tunnel to that client. From time to time the sound starts to crackle a bit, a known problem of those pulse tunnels. If it happens, just tell your PVA to restart the client and it will rewire the tunnel. The crackling will stop.

If your sound starts stuttering, your client device is poorly connected to the net or setuped in some unusally way. From observation: Pinephones have issues in the first 1-2 minutes after the tunnel started, but catch up later. A wild guess here: could be wlan/wifi throtteling. 

A working conncection takes around 200kB/s per client. Even with 2,4GHz wifi you can run a couple of clients, but remember: If someone on your channel sends MB/s traffic, your available bandwith can be shorten to a point, where the clients stutter. It will autocorrect itself with enough bandwith available.

### Config-Options:

```
ip:    client ip i.e. 192.168.178.3
user:  desktopuser of the running session of the client. You need a running desktop session as you need a running pipewireserver
key:   path/to/your/openssh.keyfile  or the word "default" for automatic key selection by ssh
name:  A nice simple Name for your Device. One word is recommended. 
sport: port for the speaker-tunnel. Default: 4656
mport: port for the microphone-tunnel. Default: 4657
```

It's possible to have more than one cluster control server running, if the local cluster configs use different ports to connect to. Depending on your usecase, this can become very handy.

## Video Streaming 

You can stream any video from your harddrives to a cluster client. A command in the form of "stream enterprise to tablet" is enough. PVA uses FFMPEG on the server and client side to transfer a FullHD stream with 3 Mb/s to your devive. As all streaming options for clients have defaults, you do not need to change your config, except, you need to use a different resolution/bandwith for your client device.

You can also stream your desktop screen + audio to any client. 

Videostreaming will continue if PVA gets stopped in mid streaming. **There is no recovery at startup atm.**

### Config-Options:

```
streamresolution: WidthxHeight. Defaults to "1920x1080" 
streamport: port for the videostream-tunnel. Defaults to "9999"
streamvideorate: defaults to "3000" => 3000 Kb/s . Note: PVA adds the necessary "k" to the number. 
streamaudiorate: defaults to "48000". No need to change this
desktopresolution: defaults to "1920x1080"
desktopdisplay: defaults to ":1"
desktopaudio: SOURCE for capturing audio. Default: see 50-plugin-cluster.conf
```
	
## ChatGPT Support

To have ChatGPT support, you need an account at open.ai.
	
If you do not directly talk to PVA(identified by your keyword) or have a REACTION pattern in place, everything you say, will be send to Open.AI
	
**Because of it, this feature is disabled by default. It will violate our privacy for sure!**
	
To not send more nonsense from the STS as necessary, you need at least four words in your sentence to trigger chatgpt support in freetalk-mode. This is due to the-ghost-words-problem with vosk. It recognizes stuff, noone said out of white noise.

"ai.py" is included under the MIT license : https://github.com/reorx/ai.py/blob/master/LICENSE
	
### Steps:
	
start ai.py and follow instructions to add your OpenAI API key to ai.py's config. 
Enable chatgpt support in a local userbased config file, and restart PVA.
	
What you can expect:
	
Shitload of FUN with misunderstanding STT software (vosk) in conjunction with sometimes unhelpfull, but entertaining answeres to stuff, noone had said ;)
	
TBH: the usability of freetalk-mode is limited. If your super hard to yourself and do only talk in front of your pc, if you have a question, it can be helpful. If you comment your daily life in front of your pc, you will get useless feedback. That's mainly, as you need an AI system to decide if what you just said, makes sense at all and should be sent to ChatGPT ;) As a solution to this problem, a workmode option has been added : 

### Modes

- "freetalk"  talk to your pva as you would do to a human. Condition: no configured reaction has been triggered and the text has a minimum of 4 words required to be recognized as a worthy sentence to be send. 
- "keyword"   set a keyword to react on and use it to address chatgpt
- "gapfiller" this mode reacts to the main pva keyword, but only, if no internal or external command was found and no other subcomponent i.e. a plugin , reacted to your command.

Now you can decide how hard it shall be to use ChatGPT in your environment. See the default ChatGPT config for details.

With **0.3.0** of ai.py we got conversation mode. This means, old data gets send with the new data as history, so chatgpt has context of what it answered.

## LLM support via Alpaca 

To have LLM support, you need to install OLLAMA yourself [default] or use ALPACA to install and run it for you.
Those LLM run fully localized, so there is no dataprotection issue at hand nor an internet connection required.

**WARNING** a LLM consumes a shitload of memory. You will run into Out-of-Memory-Issues if you have less than 16 GB installed!

Most config options are decribed above for ChatGTP except:

ai:"port","11434"
ai:"host","localhost"

post and host descripte where the OLLAMA Server can befound, which does not need to be 127.0.0.1, if you have a company and want only one AI server.

Use Alapca to install the model you want and add the ID here:

ai:"model","llava:latest"

For IMAGERecognition features, you need to specify the video device, resolution and jpeg quality you want you images taken with. The image will be stored in /tmp/webcam.jpg

ai:"device","/dev/video1"
ai:"resolution","1920x1080"
ai:"quality","70"

if we capture a desktop and crop as filled with a resolution, the image will be cropped to it. THis makes it easier to analyse the image,
as normaly the entire screen would be cropped. This has been added for multidisplay setups, which confuse the recognition service a bit.

ai:"crop","1920x1080+0+0"

The "AI" server tends to answere image describtions in english, so we tell it to use german instead. Change it to any language you want.

ai:"languageprompt","antworte in deutsch."

Example of installed and aliased models:

ai:"models","normal=llava-llama3:latest,mond=moondream:1.8b-v2-fp16,lava=llava:latest"

If you have a webcam installed, you can tell your assistant to identify objects for you, images you found via a picture search ( see above ) or analyse your desktop for you:

command:"sprachmodell wechseln zu .*","AISWAPMODEL","",""
command:"künstliche intelligenz wechsel zu .*","AISWAP","",""

command:"was ist auf dem bild zu sehen","AIIDENTIFYIMAGE","",""
command:"was ist auf den bildern zu sehen","AIIDENTIFYIMAGE","",""
command:"was halte ich in die kamera","AIIDENTIFYCAM","",""
command:"kamerabild","AIIDENTIFYCAMFREE","",""
command:"was|siehst|du|auf|desktop","AIIDENTIFYDESKTOP","",""
command:"was|siehst|du|auf|bildschirm","AIIDENTIFYDESKTOP","",""
command:"wer|ist|auf|ganzem|bildschirm","AIIDENTIFYFULLDESKTOP","",""
command:"was|ist|auf|ganzem|bildschirm","AIIDENTIFYFULLDESKTOP","",""
command:"wer|ist|auf|bildschirm","AIIDENTIFYDESKTOP","bildschirm",""
command:"was|ist|auf|bildschirm","AIIDENTIFYDESKTOP","bildschirm",""
command:"was siehst du","AIIDENTIFYCAM","",""
command:"wen siehst du","AIIDENTIFYCAM","",""

Remove the chat history. The history start new every PVA startup. If you had analysied some images, the "ai" is confused with it's old 
answeres, so from time to time, starting over fresh helps:

command:"neues gespräch","AICLEARHISTORY","",""

As these commands are only the trigger, you can add questions or restrictions as you like. 

Example:

"was siehst du?" => describe the cam picture
"was siehst du beschreibe mir das objekt im vordergrund genauer " => describe the cam picture and analyse the object in front specifically.

"was siehst du auf dem linken bildschirm aus welchem Film stammt das bild" => describe the left part of the desktop. 

If you have more than one display connected to your pc and your not precise in what you want to be analysied, you may end up with a description of the menu card from a restaurant in cleveland ;)

Have fun with it :)
