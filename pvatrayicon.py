#!/usr/bin/python

import os
import gi
import sys

gi.require_version('Gtk', '3.0')

from gi.repository import Gtk as gtk, AppIndicator3 as appindicator

def main():
  indicator = appindicator.Indicator.new("customtray", "pda", appindicator.IndicatorCategory.APPLICATION_STATUS)
  indicator.set_status(appindicator.IndicatorStatus.ACTIVE)
  indicator.set_menu(menu())
  gtk.main()

def menu():
  menu = gtk.Menu()
 
  command_two = gtk.MenuItem(sys.argv[1] +' starten')
  command_two.connect('activate', start)
  menu.append(command_two)

  command_one = gtk.MenuItem(sys.argv[1] +' beenden')
  command_one.connect('activate', kill)
  menu.append(command_one)

  exittray = gtk.MenuItem('Tray beenden')
  exittray.connect('activate', quit)
  menu.append(exittray)
  
  menu.show_all()
  return menu
  
def start(_):
  os.system("/usr/share/pva/pva &");

def kill(_):
  os.system("killall -9 python3")

def quit(_):
  gtk.main_quit()

if __name__ == "__main__":
  main()
