#!/usr/bin/env python3

import argparse
import os
import queue
import sounddevice as sd
import vosk
import sys
#import socket
#import ssl

q = queue.Queue()

def int_or_str(text):
    """Helper function for argument parsing."""
    try:
        return int(text)
    except ValueError:
        return text

def callback(indata, frames, time, status):
    """This is called (from a separate thread) for each audio block."""
    if status:
        print(status, file=sys.stderr)
    q.put(bytes(indata))

parser = argparse.ArgumentParser(add_help=False)
parser.add_argument(
    '-l', '--list-devices', action='store_true',
    help='show list of audio devices and exit')
args, remaining = parser.parse_known_args()
if args.list_devices:
    print(sd.query_devices())
    parser.exit(0)
parser = argparse.ArgumentParser(
    description=__doc__,
    formatter_class=argparse.RawDescriptionHelpFormatter,
    parents=[parser])
parser.add_argument(
    '-m', '--model', type=str, metavar='MODEL_PATH',
    help='Path to the model')
parser.add_argument(
    '-d', '--device', type=int_or_str,
    help='input device (numeric ID or substring)')
parser.add_argument(
    '-r', '--samplerate', type=int, help='sampling rate')
args = parser.parse_args(remaining)

try:
    if args.model is None:
        args.model = "model"
    if not os.path.exists(args.model):
        print ("Please download a model for your language from https://alphacephei.com/vosk/models")
        print ("and unpack as 'model' in the current folder.")
        parser.exit(0)
    if args.samplerate is None:
        device_info = sd.query_devices(args.device, 'input')
        # soundfile expects an int, sounddevice provides a float:
        args.samplerate = int(device_info['default_samplerate'])

    model = vosk.Model(args.model)

    with sd.RawInputStream(samplerate=args.samplerate, blocksize = 8000, device=args.device, dtype='int16',
                            channels=1, callback=callback):
            print('#' * 80)
            print('Press Ctrl+C to stop the recording')
            print('#' * 80)

            rec = vosk.KaldiRecognizer(model, args.samplerate)
            while True:
                data = q.get()
                if rec.AcceptWaveform(data):
                    # print(rec.Result()
                    str = rec.Result();
#                    os.system( "java PVA '"+ str.replace("'","")  +"'");
                    os.system( "echo '"+ str.replace("'","") +"' | openssl s_client -connect 127.0.0.1:39999 -nbio 1>/dev/null 2>/dev/null");
# in case anyone is interessted in a python solo solution, this is the way to go, if you want to not have a reliable ssl channel
#                   context = ssl.create_default_context()
#                   with socket.create_connection(("127.0.0.1", 39999)) as sock:
#                   	context.check_hostname = False;
#                   	context.verify_mode = False;
#                   	with context.wrap_socket(sock, server_side=False, server_hostname=None) as ssock:
#                   		print(ssock.version())
#                   		ssock.send(str.encode('utf-8'))
#                   		ssock.close()
#                   	sock.close()
#
# because this is what happens:
# working - Nicht für mich gedacht:test test
# not working - Thu Jul 14 13:30:43 CEST 2022: Server: javax.net.ssl.SSLException: Connection reset
# not working - Thu Jul 14 13:30:46 CEST 2022: Server: javax.net.ssl.SSLException: Connection reset
# not working - Thu Jul 14 13:30:53 CEST 2022: Server: javax.net.ssl.SSLException: Connection reset
# 3 / 4 of all TLS connects to the java server fail with python.

except KeyboardInterrupt:
    print('\nDone')
    parser.exit(0)
except Exception as e:
    parser.exit(type(e).__name__ + ': ' + str(e))
