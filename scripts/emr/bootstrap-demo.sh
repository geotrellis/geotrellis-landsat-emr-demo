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
        --s3u=*)
            S3U="${i#*=}"
            shift;;
        --backend=*)
            BACKEND="${i#*=}"
            shift;;
    esac
done

set -x

BACKEND=${BACKEND:-accumulo}
SERVER_RUN_CMD="accumulo accumulo `hostname` root secret"

case $BACKEND in
    "accumulo")
          shift;;
    "cassandra")
        SERVER_RUN_CMD="cassandra `hostname` `hostname`"
        shift;;
    "file")
        SERVER_RUN_CMD="local /tmp/catalog"
        shift;;
    "hadoop")
        SERVER_RUN_CMD="hdfs /catalog"
        shift;;
    "s3")
        SERVER_RUN_CMD="s3 key prefix"
        shift;;
    "hbase")
        SERVER_RUN_CMD="hbase `hostname` `hostname`"
        shift;;
esac
# Download Tile Server
aws s3 cp $TILE_SERVER_JAR /tmp/tile-server.jar
aws s3 cp $S3U/backend-profiles.json /tmp/backend-profiles.json
aws s3 cp $S3U/input.json /tmp/input.json
aws s3 cp $S3U/output.json /tmp/output.json

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
     /tmp/tile-server.jar $SERVER_RUN_CMD
post-stop exec sleep 60
" | sudo tee /etc/init/tile-server.conf

# Start Static Web Server
aws s3 cp $SITE_TGZ /tmp/site.tgz
sudo chmod 644 /var/www/html/*
sudo chmod 755 /var/www/html
sudo tar -xzf /tmp/site.tgz -C /var/www/html
sudo mkdir -p /tmp/catalog/attributes
sudo mkdir -p /tmp/catalog-cache
sudo chmod -R 777 /tmp/catalog
sudo chmod -R 777 /tmp/catalog-cache
