# Feedly auto tagging
A simple proof of concept machine learning approach to automatically tag articles 
in [Feedly](http://www.feedly.com) based on the recent tagging activity.
The only additional information needed is a valid API key which can be obtained
from https://feedly.com/v3/auth/dev .

The whole project is aimed to run on a Raspberry PI. A more in depth blog post 
[can be found on my website](http://www.locked.de/?p=1225).

# How to run
```
git clone https://github.com/locked-fg/Feedly-Autotagging.git feedly-tagger
```
Add a cron job that calles a shell script:
```
#/!bin/bash
set -e
cd ~/feedly-tagger
git fetch --all
git reset --hard origin/master
mvn clean compile assembly:single
java -jar target/*.jar hereComesYourApiKey auto
```