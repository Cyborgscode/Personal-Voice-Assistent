
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
"öffne  {TERM}
	
"ich möchte {TERM} hören"		-> search for musik files matching TERM, where TERM kann have more than one word. on Success the audioplayer gets informed

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
