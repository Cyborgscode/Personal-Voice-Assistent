#!/usr/bin/python3

# ATTN: For this to work, you need F5-TTS installed from github, globally or in your home dir.

from gradio_client import Client, handle_file

import sys
import os

n = len(sys.argv)

if ( n == 2 ):
	audio=handle_file('/usr/share/pva/f5-tts/voices/default.wav')
	file = open('/usr/share/pva/f5-tts/voices/default.txt',"r")
	rtext= file.readline()
	file.close()
	text=sys.argv[1]
else:
	audio=handle_file('/usr/share/pva/f5-tts/voices/'+ sys.argv[1] +'.wav')
	file = open('/usr/share/pva/f5-tts/voices/'+ sys.argv[1] +'.txt',"r")
	rtext= file.readline()
	file.close()
	text=sys.argv[2]

client = Client("http://127.0.0.1:7860/")
result = client.predict(
		new_choice="F5-TTS",
		api_name="/switch_tts_model"
)
# print(result)

# Next predict() is only required if you are german
# you can find those files here: https://huggingface.co/marduk-ra/F5-TTS-German/tree/main
# English users do not need those:

result = client.predict(
		custom_ckpt_path="/usr/share/pva/f5-tts/marduk-german/f5_tts_german_1010000.safetensors",
		custom_vocab_path="/usr/share/pva/f5-tts/marduk-german/vocab.txt",
		api_name="/set_custom_model"
)

# print(result)
result = client.predict(
		ref_audio_input=audio,
		ref_text_input=rtext,
		gen_text_input=text,
		remove_silence=False,
		cross_fade_duration_slider=0.15,
		speed_slider=1,
		api_name="/basic_tts"
)
print(result[0])
os.system( "play '"+ result[0] +"' 2>/dev/null");
