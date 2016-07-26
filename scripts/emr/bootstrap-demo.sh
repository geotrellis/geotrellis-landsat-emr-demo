#!/bin/sh

for i in "$@"
do
    case $i in
        --tsj=*)
            TILE_SERVER_JAR="${i#*=}"
            shift;;
        --site=*)
            SITE_TGZ="${i#*=}"
            shift;;
    esac
done

set -x

# Download Tile Server
aws s3 cp $TILE_SERVER_JAR /tmp/tile-server.jar

echo "\
description \"Landsat Demo Tile Server\"
start on started hadoop-hdfs-namenode
stop on stopping hadoop-hdfs-namenode
respawn
respawn limit unlimited
exec spark-submit --master yarn-client \
     --driver-memory 5G --driver-cores 4 \
     --executor-cores 2 --executor-memory 5G \
     --conf spark.dynamicAllocation.enabled=true \
     /tmp/tile-server.jar hbase `hostname` `hostname`
post-stop exec sleep 60
" | sudo tee /etc/init/tile-server.conf

# Start Static Web Server
aws s3 cp $SITE_TGZ /tmp/site.tgz
sudo tar -xzf /tmp/site.tgz -C /var/www/html
sudo chmod 644 /var/www/html/*
sudo chmod 755 /var/www/html
