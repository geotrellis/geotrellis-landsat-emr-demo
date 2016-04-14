#!/bin/sh

spark-submit \
--class demo.LandsatIngestMain \
--master yarn-cluster \
--driver-memory 10G \
--executor-cores 4 \
--executor-memory 10G \
--conf spark.dynamicAllocation.enabled=true \
--conf spark.yarn.executor.memoryOverhead=512 \
--conf spark.yarn.driver.memoryOverhead=512 \
s3://geotrellis-test/emr/ingest.jar \
--layerName landsat \
--polygonUri s3://geotrellis-test/emr/tristate.json \
--output accumulo \
--params instance=accumulo,table=tiles,user=root,password=secret,zookeeper=ip-172-31-9-181
#,ingestPath=hdfs://ip-172-31-9-181.ec2.internal:8020/ingest
