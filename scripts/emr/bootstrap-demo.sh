#!/bin/sh

for i in "$@"
do
    case $i in
        --tsj=*)
            TILE_SERVER_JAR="${i#*=}"
            shift;;
        --tsm=*)
            TILE_SERVER_MAIN="${i#*=}"
            shift;;
        --site=*)
            SITE_TGZ="${i#*=}"
            shift;;
    esac
done

set -x

# Start Static Web Server
aws s3 cp $SITE_TGZ /tmp/site.tgz
mkdir -p /tmp/site
tar -xzf /tmp/site.tgz -C /tmp/site

sudo yum -y install docker
sudo usermod -aG docker hadoop
sudo service docker start
sudo docker run --name demo-site -p 8080:80 -v /tmp/site:/usr/share/nginx/html:ro -d nginx

# Start Tile Server
aws s3 cp $TILE_SERVER_JAR /tmp/tile-server.jar

# wait until after accumulo is started
echo "nohup spark-submit --master yarn-client \
      --driver-memory 5G --driver-cores 4 \
      --executor-cores 2 --executor-memory 5G \
      --conf spark.dynamicAllocation.enabled=true \
      --class $TILE_SERVER_MAIN /tmp/tile-server.jar \
      accumulo accumulo localhost root secret&" | at now + 4 min
