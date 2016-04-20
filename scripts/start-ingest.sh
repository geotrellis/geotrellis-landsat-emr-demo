#!/bin/sh

for i in "$@"
do
case $i in
    --cluster-id=*)
    CLUSTER_ID="${i#*=}"
    shift
    ;;

    --polygon=*)
    POLYGON_FILE="${i#*=}"
    shift
    ;;
esac
done

if [[ -z $CLUSTER_ID || -z $POLYGON_FILE ]]
then
  echo "usage: start-ingest --cluster-id=<CLUSTER_ID> --polygon=<QUERY_POLYGON_GEOJSON_FILE>"
  exit
fi

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $DIR/environment.sh

aws s3 cp $POLYGON_FILE $EMR_TARGET/polygon.json

ARGS=spark-submit,--master,yarn-cluster
ARGS=$ARGS,--class,demo.LandsatIngestMain
ARGS=$ARGS,--driver-memory,10G
ARGS=$ARGS,--executor-cores,4
ARGS=$ARGS,--executor-memory,10G
ARGS=$ARGS,--conf,spark.dynamicAllocation.enabled=true
ARGS=$ARGS,--conf,spark.yarn.executor.memoryOverhead=512
ARGS=$ARGS,--conf,spark.yarn.driver.memoryOverhead=512
ARGS=$ARGS,$EMR_TARGET/ingest-assembly-0.1.0.jar
ARGS=$ARGS,--layerName,landsat
ARGS=$ARGS,--polygonUri,$EMR_TARGET/polygon.json
# ARGS=$ARGS,--limit,4
ARGS=$ARGS,--output,accumulo
ARGS=$ARGS,--params,\"instance=accumulo,table=tiles,user=root,password=secret\"

aws emr add-steps --cluster-id $CLUSTER_ID --steps Type=CUSTOM_JAR,Name=Ingest,Jar=command-runner.jar,Args=[$ARGS]
