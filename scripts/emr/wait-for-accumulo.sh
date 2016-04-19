#!/bin/sh

# Wait until accumulo folder is available on HDFS
until hdfs dfs -ls /accumulo
do
  echo "Waiting for accumulo init ..."
  sleep 5
done

# Create hdfs home folder so we can run spark-submit as accumulo user
hdfs dfs -mkdir /user/accumulo
hdfs dfs -chown accumulo /user/accumulo

# Create ingest folder to be used by geotrellis
hdfs dfs -mkdir /ingest
hdfs dfs -chown accumulo /ingest
