#!/bin/bash
git pull && ./gradlew shadowJar && screen -X -S fengsheng quit && screen -dmS fengsheng java -jar build/libs/fengsheng-1.0-SNAPSHOT-all.jar && screen -ls
