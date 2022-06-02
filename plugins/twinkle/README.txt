/* 
    TWINKLE SUPPORT
    
    Status: PVA gets connected to incoming Call and can talk and receive cmds

    TODO: find a way to autodetect required PULSEAUDIO Sinks and Sources, but I'm afraid, will be done manually :(

    01.06.2022: It's to decide how to place the required configs into twinkle, the home and pva

*/

HOW TO INSTALL:
===============

1. Twinkle:

Fedora: sudo dnf install twinkle

2. add new profile to Twinkle for each line, you wanne have PVA to connect with.
  
3. edit profile scripts tab and add:

INCOMING CALL: /home/$USER/.local/bin/twinkle.anrufstart
INCOMING CALL FAIL: /home/$USER/.local/bin/twinkle.anrufende
CALL ENDED LOCALLY: /home/$USER/.local/bin/twinkle.anrufende
CALL ENDED REMOTLY: /home/$USER/.local/bin/twinkle.anrufende

4. mkdir -p /home/$USER/.local/bin/

5. copy the two twinkle files to your home ~/.local/bin/

6. edit those two files and replace YOUR SINKS & SOURCES

ATTN: 

The files contain two SINKS/SOURCE(Monitors) because I have a HDMI display with sound output and a USB MIC. 

If you only have the mainboard sinks/sources(monitors) you need to connect the input source of PVA to the output (monitor) of Twinkle and vice versa.

If you have multiply sinks/sources, it's better to use two different devices which get cross connected, because the call is not mixed with PVA's answeres.

HINT: it's better to keep the PVA output device as usual and change Twinkle output, because it's a f****** nightmare to redirect the output of SOX ( say/gsay etc ) when it's not playing sound. It's not just a nightmare, it's simply impossible, without altering the PA sinkstate database.

HOW TO FIND YOR SINK & SOURCE?
==============================

# pactl list sources | grep Name
# pactl list sinks | grep Name

Special Thanks to the ArchLinux User "quequotion":

 edited your bash script, which unfortunatly only works in english. Now it does always work and it is compatible with PA 15.0 ;)






