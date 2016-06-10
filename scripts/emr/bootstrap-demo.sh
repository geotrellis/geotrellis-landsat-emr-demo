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
        --at)
            FLAG_AT=1
            shift;;
        --run)
            FLAG_RUN=1
            shift;;
    esac
done

set -x

THIS_SCRIPT="$(realpath "${BASH_SOURCE[0]}")"
if [ $FLAG_RUN ]; then
    # Finally we loop on spark-submit, which should not terminate without error
    while true
    do
        spark-submit --master yarn-client \
                     --driver-memory 5G --driver-cores 4 \
                     --executor-cores 2 --executor-memory 5G \
                     --conf spark.dynamicAllocation.enabled=true \
                     --class $TILE_SERVER_MAIN /tmp/tile-server.jar \
                     accumulo accumulo localhost root secret
        sleep 60
    done
elif [ $FLAG_AT ]; then
    # Second we fork unto process out of at process
    nohup $THIS_SCRIPT --run --tsm=$TILE_SERVER_MAIN &
    exit 0
else
    # First we schedule ourselves to run via at
    echo "$THIS_SCRIPT --at --tsm=$TILE_SERVER_MAIN" | at now + 5 min
fi

# Only attainable through initial run:

# Start Static Web Server
aws s3 cp $SITE_TGZ /tmp/site.tgz
mkdir -m 755 -p /tmp/site
tar -xzf /tmp/site.tgz -C /tmp/site
chmod 644 /tmp/site/*
chmod 755 /tmp/site

sudo yum -y install docker
sudo usermod -aG docker hadoop
sudo service docker start
sudo docker run --name demo-site -p 8080:80 -v /tmp/site:/usr/share/nginx/html:ro -d nginx

# Download Tile Server
aws s3 cp $TILE_SERVER_JAR /tmp/tile-server.jar
