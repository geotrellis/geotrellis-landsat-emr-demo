#!/bin/sh

for i in "$@"
do
case $i in
    --cluster-id=*)
    CLUSTER_ID="${i#*=}"
    shift
    ;;
    --bbox=*)
    BBOX="${i#*=}"
    shift
    ;;
esac
done

if [[ -z $CLUSTER_ID || -z $BBOX ]]
then
  echo "usage: start-ingest --cluster-id=<CLUSTER_ID> --BBOX=<xmin,ymin,xmax,ymax>"
  exit
fi

EXECUTOR_MEMORY=10G
EXECUTOR_CORES=4
YARN_OVERHEAD=520

ARGS="spark-submit,--master,yarn-cluster,\
--class,demo.LandsatIngestMain,\
--driver-memory,$EXECUTOR_MEMORY,\
--executor-cores,$EXECUTOR_CORES,\
--executor-memory,$EXECUTOR_MEMORY,\
--conf,spark.dynamicAllocation.enabled=true,\
--conf,spark.yarn.executor.memoryOverhead=$YARN_OVERHEAD,\
--conf,spark.yarn.driver.memoryOverhead=$YARN_OVERHEAD,\
$EMR_TARGET/ingest-assembly-0.1.0.jar,\
--layerName,landsat,\
--bbox,\"$BBOX\",\
--startDate,2015-08-01,\
--endDate,2016-01-01,\
--output,accumulo,\
--params,\"instance=accumulo,table=tiles,user=root,password=secret\",\
--limit,1"

aws emr add-steps --output text --cluster-id $CLUSTER_ID \
    --steps Type=CUSTOM_JAR,Name=Ingest,Jar=command-runner.jar,Args=[$ARGS]
