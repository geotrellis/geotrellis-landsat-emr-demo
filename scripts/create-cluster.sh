#!/bin/sh
# Starts a long running ETL cluster.

SCRIPT_RUNNER=s3://elasticmapreduce/libs/script-runner/script-runner.jar
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
echo $DIR
aws emr create-cluster \
  --output text \
  --name "Landsat Ingest" \
  --release-label emr-4.5.0 \
  --use-default-roles \
  --configurations file://$DIR/configurations.json \
  --log-uri $EMR_TARGET/logs \
  --ec2-attributes KeyName=$KEY_NAME \
  --applications Name=Ganglia Name=Hadoop Name=Hue Name=Spark Name=Zeppelin-Sandbox \
  --instance-groups \
    Name=Master,InstanceCount=1,InstanceGroupType=MASTER,InstanceType=$MASTER_INSTANCE \
    Name=Workers,InstanceCount=$WORKER_COUNT,BidPrice=$WORKER_PRICE,InstanceGroupType=CORE,InstanceType=$WORKER_INSTANCE \
  --bootstrap-action Path=$EMR_TARGET/bootstrap-geowave.sh,Name=Bootstrap_GeoWave_Node \
  --steps \
    Type=CUSTOM_JAR,Name=WaitForInit,Jar=$SCRIPT_RUNNER,Args=[$EMR_TARGET/wait-for-accumulo.sh] \
    Type=CUSTOM_JAR,Name=TileService,Jar=$SCRIPT_RUNNER,Args=[$EMR_TARGET/tile-server.sh,$EMR_TARGET/server-assembly-0.1.0.jar]
