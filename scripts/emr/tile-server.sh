#!/bin/sh

JAR=$1

aws s3 cp $JAR /tmp/server.jar
nohup spark-submit --master yarn-client --driver-memory 5G --driver-cores 4 \
--executor-cores 4 --executor-memory 10G \
--conf spark.dynamicAllocation.enabled=true \
--class demo.Main /tmp/server.jar accumulo accumulo localhost root secret &
