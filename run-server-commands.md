http://ec2-52-53-232-9.us-west-1.compute.amazonaws.com:8088

# Spark

http://ec2-52-53-232-9.us-west-1.compute.amazonaws.com:4040

# Accumulo
http://ec2-52-53-232-9.us-west-1.compute.amazonaws.com:50095

# HDFS
http://ec2-52-53-232-9.us-west-1.compute.amazonaws.com:50070

# Mesos
http://ec2-52-53-232-9.us-west-1.compute.amazonaws.com:5050

# Marathon
http://ec2-52-53-232-9.us-west-1.compute.amazonaws.com:8080

# Grafana
http://ec2-52-53-232-9.us-west-1.compute.amazonaws.com:8090

# Build server
export SPRAY_VERSION=1.2.3
./sbt "project server" assembly
export SPRAY_VERSION=1.3.3

# Build ingest
./sbt "project ingest" assembly

# copy up server
scp -i ~/.keys/geotrellis-climate-us-west-1.pem \
  /Users/rob/proj/workshops/2016-01/demo/server/target/scala-2.10/server-assembly-0.1.0.jar \
  ubuntu@ec2-52-53-232-9.us-west-1.compute.amazonaws.com:/home/ubuntu/

# copy up ingest

scp -i ~/.keys/geotrellis-climate-us-west-1.pem \
  /Users/rob/proj/workshops/2016-01/demo/ingest/target/scala-2.10/ingest-assembly-0.1.0.jar \
  ubuntu@ec2-52-53-232-9.us-west-1.compute.amazonaws.com:/home/ubuntu/


run local /Volumes/Transcend/data/workshop/jan2016/data/landsat-catalog
reStart local /Volumes/Transcend/data/workshop/jan2016/data/landsat-catalog

# ssh master
ssh -i ~/.keys/geotrellis-climate-us-west-1.pem ubuntu@ec2-52-53-232-9.us-west-1.compute.amazonaws.com

# Delete metadta
deleterows -b RCP45-Temperature-Min__._ -e RCP45-Temperature-Min__.__8
rellis.spark.io.package$AttributeNotFoundError: Attribute times not found for layer Layer(name = "RCP45-Temperature-Min", zoom = 0)


# Start the shuffle service 7337
sudo /usr/lib/spark/sbin/start-mesos-shuffle-service.sh
sudo /usr/lib/spark/sbin/stop-mesos-shuffle-service.sh
[["hostname", "UNIQUE"]]

# job to kill shuffle files
sudo rm -rf /media/ephemeral0/blockmgr-*
sudo rm -rf /media/ephemeral1/blockmgr-*


# Run Ingest Accumulo with shuffle service
spark-submit \
--class demo.NEXIngest \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.shuffle.service.enabled=true \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
ingest-assembly-0.1.0.jar \
accumulo \
1

# Run Ingest Accumulo
spark-submit \
--class demo.NEXIngest \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
ingest-assembly-0.1.0.jar \
accumulo \
1

# Run Ingest Accumulo custom
spark-submit \
--class demo.NEXIngest \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
ingest-assembly-0.1.0.jar \
custom-accumulo \
1


# Run local ingest


<!-- # Run Ingest Accumulo -->
<!-- spark-submit \ -->
<!-- --class demo.NEXIngest \ -->
<!-- --master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \ -->
<!-- --conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \ -->
<!-- --conf spark.driver.cores=4 \ -->
<!-- --conf spark.driver.memory=4g \ -->
<!-- --conf spark.executor.memory=12g \ -->
<!-- --conf spark.mesos.coarse=true \ -->
<!-- --conf spark.default.parallelism=1000 \ -->
<!-- ingest-assembly-0.1.0.jar \ -->
<!-- accumulo -->


## Accumulo shell

config -t tiles -s table.split.threshold=200M
compact -t tiles


# Run S3 server
spark-submit \
--class demo.Main \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.default.parallelism=20 \
server-assembly-0.1.0.jar \
s3 \
geotrellis-climate-catalogs \
ensemble-temperature-catalog



# Run Accumulo server
spark-submit \
--class demo.Main \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.default.parallelism=20 \
server-assembly-0.1.0.jar \
accumulo \
geotrellis-accumulo-cluster \
zookeeper.service.geotrellis-spark.internal \
root \
secret

# Run Custom Accumulo server
spark-submit \
--class demo.Main \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.default.parallelism=20 \
server-assembly-0.1.0.jar \
custom-accumulo \
geotrellis-accumulo-cluster \
zookeeper.service.geotrellis-spark.internal \
root \
secret



## Local accumulo ingest
spark-submit \
--class demo.NEXIngest \
ingest-assembly-0.1.0.jar \
custom-local \
gis \
localhost \
root \
secret

custom-local gis localhost root secret

# Run local custom Accumulo server
custom-local gis localhost root secret


### From other workshop
## FindMinMax
spark-submit \
--class demo.FindMinMaxTime \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
demo-assembly-0.1.0.jar

## Ingest S3
spark-submit \
--class demo.Ingest \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
demo-assembly-0.1.0.jar \
s3 \
30

## Ingest Accumulo
spark-submit \
--class demo.Ingest \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.storage.memoryFraction=0.4 \
demo-assembly-0.1.0.jar \
accumulo \
15

## ServerExample S3
spark-submit \
--class demo.ServerExample \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.storage.memoryFraction=0.3 \
--conf spark.default.parallelism=50 \
demo-assembly-0.1.0.jar \
s3

## ServerExample Accumulo
spark-submit \
--class demo.ServerExample \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
demo-assembly-0.1.0.jar \
accumulo

spark-shell \
--master mesos://zk://zookeeper.service.geotrellis-spark.internal:2181/mesos \
--jars demo-assembly-0.1.0.jar

## local

spark-submit \
--class demo.FindMinMaxTime \
target/scala-2.10/demo-assembly-0.1.0.jar \
file://`pwd`/demo/data/rainfall-wm

spark-submit \
--class demo.Ingest \
target/scala-2.10/demo-assembly-0.1.0.jar \
local \
file://`pwd`/demo/data/rainfall-wm \
file://`pwd`/demo/data/catalog

spark-submit \
--class demo.ServerExample \
target/scala-2.10/demo-assembly-0.1.0.jar \
local \
file://`pwd`/demo/data/catalog



## Running reproject locally

spark-submit \
../code/reproject_to_s3.py \
file://`pwd`/demo/data/rainfall \
file://`pwd`/demo/data/rainfall-wm \
--extension tif


## Accumulo shell

config -t tiles -s table.split.threshold=200M
compact -t tiles

## Polygon for testing (Africa)

{
    "type": "Polygon",
    "coordinates": [
        [
            [
                -20.214843749999996,
                -37.30027528134431
                ],
            [
                -20.214843749999996,
                37.16031654673677
                ],
            [
                53.96484375,
                37.16031654673677
                ],
            [
                53.96484375,
                -37.30027528134431
                ],
            [
                -20.214843749999996,
                -37.30027528134431
            ]
        ]
    ]
}
