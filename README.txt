/*

PVA is coded by Marius Schwarz in 2021

This software is free. You can copy it, use it or modify it, as long as the result is also published on this condition.
You only need to refer to this original version in your own readme / license file. 

*/


Hi,

This project depends heavily on the AlphaCephei Software VOSK, which can be found here:

https://alphacephei.com/vosk/

Language models for 18 different languages can be found here: 

https://alphacephei.com/vosk/models

To run it, you need at least:

    Python3
    pip3
    portaudio
    mbrola             (found on github)
    espeak
    vosk               (found on github)
    org.json.* java classes

O=== How to install:

We don't need fancy frameworks* or complicated makefiles, so relax, it's just done in about 5 minutes :) The author uses Fedora as OS,
so if you run Ubuntu or Arch, your install commands will vary a bit.

*) looks like thats not entirely true, as we depend on org.json.* classes from here https://github.com/stleary/JSON-java/releases/tag/20210307

O== if you want to use GTTS instead of espeak/mbrola

your need to checkout gsay, change your config to use gsay as "say" app and install gtts for your distro:

i.E. Fedora: dnf install gtts

## WARNING: 
## your personal privacy is at risk, if you use gtts, as the text of the what PVA answeres ( which includes repeats you what you said ) 
## is tranfsered to a Google server, for which you need an active internet connection too. 

O== Espeak:

sudo dnf  -y install espeak

O== Mbrola: ( OPTIONAL, but than you have to live with espeak ;) and you have to change the say script )

Download:

https://github.com/numediart/MBROLA
https://github.com/numediart/MBROLA-voices

Compile: ( needs gcc )

cd MBROLA-master; make; cp Bin/mbrola /usr/local/sbin/“

experiment a bit with the best voice for you. You will find the "say" Bash script in this repo, but it will default to de5, as it's setuped for german.
Just replace de5 with your prefered language and don't forget to translate the responses in PVA.java ;)

If you need help with mbrola: https://marius.bloggt-in-braunschweig.de/2021/03/24/mbrola-etwas-bessere-sprachsynthese/

O== Python3 + Pip3 + Portaudio:

pip3 install sounddevice
sudo dnf install python3-pyaudio espeak

O== Vosk:

On your desktop you can directly install vosk :

pip3 install vosk

If you wanne run this on a Pinephone, as recently demonstrated, you need atleast this BETA version:

pip3 install https://github.com/alphacep/vosk-api/releases/download/v0.3.30/vosk-0.3.30-py3-none-linux_aarch64.whl

check if there is a newer version, before you run this command.

Demonstration: http://static.bloggt-in-braunschweig.de/Pinephone-PVA-TEST1.mp4

O== Getting the right language model:

Download them here:

https://alphacephei.com/vosk/models

There are small models, with a smaller stock of words, but it may already be enough for your tasks, or you can use the big ones ( ~1.9 GB ), which have a lot more words, but tend to have false positives for exactly this reason :) i.e. in german "füge" and "flüge" sound very similar and this gives us a relativly high error rate. You should use the small model on the Pinephone, due to it's size advantage.

Installation: (replace the modelfile with your filename! )

mkdir Programme
cd Programme/
mkdir vosk
cd vosk/
git clone https://github.com/alphacep/vosk-api
cd vosk-api/python/example/
unzip vosk-model-small-de-0.15.zip
ln -s vosk-model-small-de-0.15 model
./test_microphone.py

Your nearly done. The link in the above script gives us the opportunity to change the model on the fly, i.e. with a voice cmd given to carola ;)

cp test_microphone.py pva.py 

edit pva.py and adjust it to this new while loop:

            rec = vosk.KaldiRecognizer(model, args.samplerate)
            while True:
                data = q.get()
                if rec.AcceptWaveform(data):
                    # print(rec.Result())
                    os.system( "java PVA '"+ rec.Result()  +"'");
#                else: 
#                   print("\r");
#                    print(rec.PartialResult())
                if dump_fn is not None:
                    dump_fn.write(data)

it's just 4 lines of edit, can do this ;) 

copy the say script to /usr/local/bin or sbin and your finished installing.


<== Carola, a PVA ==>


Carola is the keyword i use to address the pva. You can change this in the pva.conf file you have to create ;)

HINT:

In case your desired app does not have the same functionality as the default apps, which are the default apps for a reason, create bash wrapper scripts and use those.

SETUP:

You noticed the "pva.conf.default" file? 

Open it with a texteditor and change(in some cases add) the required infos, i.e. the keyword "carola" if you wanne use a different one.
If you don't have the apps  in there, just install them. 

You can find qmmp & celluloid in the fedora repos, but to fully make use of qmmp, you may wanne install some plugins from rpmfusion.org. 
Jitsi ( Desktop version ) can be found on github and leave a +1 to Ingo Bauersachs, the main dev of Jitsi, you will LOVE calling people just with your voice ;)
Jitsi will be the bridge to your SIP provider, which can be your dsl or cable modem or something like SIPgate, Asterix etc.

A online CardDAV addressbook (with content) is required for some functions like sending email or calling someone. If you find a way to get those infos from the desktop contact app, please send a pull request. CardDAV has the advantage of also working in Thunderbird, which gives you a nice UI to enter your data. Also Thunderbird is the main MailAPP.

The pva.conf file is self explaning.

## Since commit #2c74e1d (16.12.2021) you can have multiply config files. 

There are two places config are found:

/etc/pva/conf.d/00-test.conf
~/.config/pva/conf.d/00-test.conf

The actual config "./pva.conf" is always loaded as the last config file, to overwrite existing, alternative settings . I.E. if you changed the searchengine via "use duckduckgo as searchengine" and you had google before, you need to save this up.

 Commit #2c74e1d is not perfect yet, we need to move parts of the none overwriteable configparts to the new system.


COMMANDS:

You will find here the commands, PVA can undertand atm. You will see, they are in german, but explained in english.
Thats because the ATM the project is hard coded for german users, but that should not stop you ;)

Checkout the PVA.java code and search for the a keyword listed below, you will notice 3 simple methods: und() oder() wort() which handle
the keywords: und => and , oder => or, wort = word => "exactly this text" . und and oder take arguments like ("word1|word2|...|wordX)" ,
in case of "und" it means ALL words have to be in the spoken text, with "oder" only ONE needs to be in it. The order does not matter.

Change the keywords and the logical pattern, thats best matching your desired language and recompile it. ( i.e. java --release 8 PVA.java ) . Done. 

This hardcoded logic will be replace with a REGEXP based datafile, so code changes are not needed anymore to translate it to other languages.

{TERM} mandatory infos needed to perform task
[TYPE] i.e. optional info i.e. number type like cellphone, work or home

Keyword-Sentences in Human Response Simulation:

"ha ha ha"
"danke"
"sehr witzig"
"das funtioniert"
"das funtioniert nicht"
"wie ist dein name"


Keywords in Bot Reaction: " Keyword " + content


"autorisierung code {TERM}"		-> give secret code to ack previous command
"autorisierung neuer code {TERM} "	-> set new secret code 

"nochmal" -> repeat last cmdline

"wie war der letzte fehler" 		-> readout last error message of selfcompile try
"wie war die fehlermeldung" 		-> readout last error message of selfcompile try

"schalte dich ab" 			-> request Alpha code and exit
"compiliere dich neu" 			-> compile the PVA.java file new

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
