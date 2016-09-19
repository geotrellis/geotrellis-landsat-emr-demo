# GeoTrellis Landsat EMR demo

This project is a demo of using GeoTrellis to ingest a set of Landsat scenes from S3 into an AWS EMR instance running Apache Accumulo and stand up tile sever that is serves the Landsat multi-band tiles as either RGB, NDVI, or NDWI layers. In addition it provides a change view between two layers, scenes captured at different time, for either NDWI or NDVI.


- [Project Strucutre](#project-structure)
- [Makefile](#makefile)
- [Running](#running)
  - [Running Locally](#running-locally)
  - [Running on EMR](#running-on-emr)
    - [Configuration](#configuration)
    - [Upload Code](#upload-code)
    - [Create Cluster](#create-cluster)
    - [Ingest](#ingest)
    - [Debugging and Monitoring](#debugging-and-monitoring)
    - [Terminate Cluster](#terminate-cluster)
- [Deployment](#deployment)
  - [Jenkins](#jenkins)

## Project Structure

This project consist of three modules:

### [ingest](./ingest)

A Scala-Spark project that implements a command-line program to query, re-project, tile and save Landsat 8 scenes as a TMS tile pyramid. The Landsat scenes are saved as multi-band tiles containing: red, green, blue, near-infrared, and QA bands.

### [server](./server)

A Scala-Spark project that implements an HTTP service providing following endpoints:

 - Catalog of available layers and scene times
 - Render layer as RGB/NDVI/NDWI
 - Render change in NDVI/NDWI between two dates
 - Calculate average NDVI/NDWI value for query polygon
 - Construct time-series of NDVI/NDWI values for query pixel

### [viewer](./viewer)

JavaScript project that queries the service and renders the layers on a Leaflet map.

## Makefile

This project is reasonably complex in that it contains sub-projects in two different languages, using two different build systems, which may be deployed in at least two different ways using tools which are highly configurable. To manage the complexity of these steps we created a [`Makefile`](./Makefile) which encapsulates the logic.

We will see them get used individually but here is the outline:

| Command          | Description
|------------------|------------------------------------------------------------|
|local-ingest      |Run `ingest` project locally                                |
|local-tile-server |Start `server` project locally                              |
|upload-code       |Upload code and scripts to S3                               |
|create-cluster    |Create EMR cluster with configurations                      |
|ingest            |Add `ingest` step to running cluster                        |
|wait              |Wait for last step to finish                                |
|proxy             |Create SOCKS proxy for active cluster                       |
|ssh               |SSH into cluster master                                     |
|get-logs          |Get spark history logs from active cluster                  |
|update-route53    |Update Route53 DNS record with active cluster ip            |
|clean             |Clean local projects                                        |

As you execute these commands you should look at the `Makefile` content and feel free to make it your own. The plethora of configuration options make many opportunities for mistakes and it is very helpful to capture the process in a script such as this.

## Running

If you were developing a project like this your first steps should be to write unit tests, then the project should be run in spark local mode, it should be tested on a cluster with limited input, and finally with full input. These steps represent increasingly longer feedback cycles and should be followed in that order to save your time.

### Running Locally

_Requires_: Spark installed locally such that `spark-submit` command is available in shell

The first thing we need to do is to create `server` and `ingest` assemblies. Assemblies are fat jars, containing all projects transitive dependencies. We need to provide them as an argument to [`spark-submit`](http://spark.apache.org/docs/latest/submitting-applications.html) command which is a shim that will provide an instance of `SparkContext` for our application.

```console
❯ ./sbt "project server" assembly
[info] Packaging /Users/eugene/proj/landsat-demo/ingest/target/scala-2.10/ingest-assembly-0.1.0.jar ...
[info] Done packaging.
[success] Total time: 31 s, completed Jun 29, 2016 2:38:20 PM

❯ ./sbt "project ingest" assembly
[info] Packaging /Users/eugene/proj/landsat-demo/server/target/scala-2.10/server-assembly-0.1.0.jar ...
[info] Done packaging.
[success] Total time: 29 s, completed Jun 29, 2016 2:39:54 PM
```

Now we can invoke `spark-submit` to run our ingest project through the Makefile. Helpfully it will echo out the command it has generated so we can inspect and verify:

```console
❯ make LIMIT=1 local-ingest
spark-submit --name "Landsat Demo Ingest" --master "local[4]" --driver-memory 4G \
ingest/target/scala-2.10/ingest-assembly-0.1.0.jar \
--layerName landsat \
--bbox 135.35,33.23,143.01,41.1 --startDate 2015-07-01 --endDate 2015-11-30 \
--output file \
--params path=catalog \
--limit 1
```

Note that we define the `LIMIT` in the make argument list, which sets its in the make environment and it gets passed as `--limit` option [parsed](./ingest/src/main/scala/demo/Config.scala) by `ingest` project. Specifically `LIMIT` will limit the result of the query to first `n` items, in whichever order the come in.

We use the `file` output, so the tiles once processed will be written to `catalog` using Avro encoding specified by GeoTrellis.

```console
❯ tree -L 2 catalog
catalog
├── attributes
│   ├── landsat__.__0__.__extent.json
│   ├── landsat__.__0__.__times.json
│   ├── landsat__.__10__.__metadata.json
│   ├── landsat__.__11__.__metadata.json
│   ├── landsat__.__12__.__metadata.json
│   ├── landsat__.__13__.__metadata.json
│   ├── landsat__.__1__.__metadata.json
│   ├── landsat__.__2__.__metadata.json
│   ├── landsat__.__3__.__metadata.json
│   ├── landsat__.__4__.__metadata.json
│   ├── landsat__.__5__.__metadata.json
│   ├── landsat__.__6__.__metadata.json
│   ├── landsat__.__7__.__metadata.json
│   ├── landsat__.__8__.__metadata.json
│   └── landsat__.__9__.__metadata.json
└── landsat
    ├── 1
    │   ├── 17592254351711
    │   └─- ...
    ├── ...
```

Now we can start our server and ask it to read the catalog:

```console
❯ make local-tile-server

spark-submit --name "Landsat Demo Service" --master "local" --driver-memory 1G \
server/target/scala-2.10/server-assembly-0.1.0.jar local catalog

❯ curl localhost:8899/catalog
{
  "layers": [{
    "name": "landsat",
    "extent": [[138.09185, 32.11207], [140.55872, 34.22866999999999]],
    "times": ["2015-11-26T01:00:00-0400"],
    "isLandsat": true
  }]
}
```

The remaining step is to start our viewer:

```console
❯ cd viewer
❯ npm install
...
❯ npm start

> geotrellis-viewer@0.0.2 start /Users/eugene/proj/landsat-demo/viewer
> node_modules/nodemon/bin/nodemon.js server/server.js --ignore components --ignore containers

[nodemon] 1.9.2
[nodemon] to restart at any time, enter `rs`
[nodemon] watching: *.*
[nodemon] starting `node server/server.js`
Express server listening on port 3000
```

[http://localhost:3000/](http://localhost:3000/) and hit the "Load" button in top right corner.


### Running on EMR

_Requires_: Reasonably up to date [`aws-cli`](https://aws.amazon.com/cli/) this document is written with version `1.10`.

To run this project on EMR we will need to allocate a cluster that has appropriate bootstrap steps to install Apache Accumulo, run our `server` project as a service on master node, run our `ingest` project as an EMR job step and upload our site to be served by `httpd` running on EMR master.

EMR is going to be using YARN to distributed our applications and manage their resource consumption, so we will need to include some YARN specific configurations in our `spark-submit` arguments.

The win here is that through EMR interface we get to refer to the whole cluster as a single unit and avoid the considerable trouble of managing the individual machines, their configuration and their cluster membership.

#### Configuration

Before anything we need to review the parameters for our cluster. They have been broken out into three sections which are imported by the `Makefile`.

 - [config-aws.mk](./config-aws.mk) AWS credentials, S3 staging bucket, subnet, etc
 - [config-emr.mk](./config-emr.mk) EMR cluster type and size
 - [config-ingest.mk](./config-ingest.mk) Ingest step parameters

You will need to modify `config-aws.mk` to reflect your credentials and your VPC configuration. `config-emr.mk` and `config-ingest.mk` have been configured with an area over Japan. Be especially aware that as you change instance types `config-emr.mk` parameters like `EXECUTOR_MEMORY` and `EXECUTOR_CORES` need to be reviewed and likely adjusted.

Aside from editing these files we have two more ways to affect the behavior of the `make` command.

 - Pass assign the variable in command line: `make NAME="My Cluster" create-cluster`
 - Instruct make overwrite defined vars with those found in the environment: `make -e create-cluster`

#### Ingest configuration

Inf the [./conf](conf) derectory provided templates for the ingest process. Detailed description can be found [there](https://github.com/geotrellis/geotrellis/blob/master/docs/spark-etl/spark-etl-intro.md).

`output.json` configurations:

##### Accumulo

```json
  "backend": {
    "type": "accumulo",
    "path": "tiles",
    "profile": "accumulo-emr"
  }
```

##### Cassandra

```json
  "backend": {
    "type": "cassandra",
    "path": "geotrellis.tiles",
    "profile": "cassandra-emr"
  }
```

##### File

```json
  "backend": {
     "type": "file",
     "path": "/tmp/catalog"
   }
```

##### Hadoop

```json
  "backend": {
    "type": "hadoop",
    "path": "/catalog"
  }
```

##### HBase

```json
  "backend": {
    "type": "hbase",
    "path": "tiles",
    "profile": "hbase-emr"
  }
```

#### Upload Code

Now that we have configured AWS credentials we need to upload relevant files to S3 such that EMR is able to reference and download them during the bootstrap phase and during the job processing. Helpfully this will trigger rebuild if make notices that any of the source files have changed.

```console
❯ make upload-code

upload: viewer/site.tgz to s3://geotrellis-test/emr/site.tgz
upload: scripts/emr/bootstrap-demo.sh to s3://geotrellis-test/emr/bootstrap-demo.sh
upload: scripts/emr/bootstrap-geowave.sh to s3://geotrellis-test/emr/bootstrap-geowave.sh
upload: scripts/emr/geowave-install-lib.sh to s3://geotrellis-test/emr/geowave-install-lib.sh
upload: server/target/scala-2.10/server-assembly-0.1.0.jar to s3://geotrellis-test/emr/server-assembly-0.1.0.jar
upload: ingest/target/scala-2.10/ingest-assembly-0.1.0.jar to s3://geotrellis-test/emr/ingest-assembly-0.1.0.jar
```

#### Create Cluster

```console
❯ make NAME="Landsat Cluster" USE_SPOT=true create-cluster
aws emr create-cluster --name "LC Cassandra"  \
--release-label emr-4.5.0 \
--output text \
--use-default-roles \
--configurations "file:///Users/eugene/proj/landsat-demo/scripts/configurations.json" \
--log-uri s3://geotrellis-test/emr/logs \
--ec2-attributes KeyName=geotrellis-emr,SubnetId=subnet-c5fefdb1 \
--applications Name=Ganglia Name=Hadoop Name=Hue Name=Spark Name=Zeppelin-Sandbox \
--instance-groups \
Name=Master,BidPrice=0.5,InstanceCount=1,InstanceGroupType=MASTER,InstanceType=m3.xlarge \
Name=Workers,BidPrice=0.5,InstanceCount=4,InstanceGroupType=CORE,InstanceType=m3.2xlarge \
--bootstrap-actions \
Name=BootstrapGeoWave,Path=s3://geotrellis-test/emr/bootstrap-geowave.sh \
Name=BootstrapDemo,Path=s3://geotrellis-test/emr/bootstrap-demo.sh,\
Args=[--tsj=s3://geotrellis-test/emr/server-assembly-0.1.0.jar,--site=s3://geotrellis-test/emr/site.tgz] \
| tee cluster-id.txt
j-2L3HJ8N2BMVDV
```

All that happened here is that the `Makefile` constructed the `aws emr create-cluster` command with considerable arguments and executed it. We can actually see references to assemblies and code that was just uploaded given as arguments to the command.

Finally `aws` command has given us a cluster id that was just created and we save it off to `cluster-id.txt` so we can refer to it in future commands.

#### Ingest

```console
❯ make LIMIT=1 ingest

aws emr add-steps --output text --cluster-id  \
--steps Type=CUSTOM_JAR,Name="Ingest japan-typhoon",Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,demo.LandsatIngestMain,\
--driver-memory,4200M,\
--driver-cores,2,\
--executor-memory,4100M,\
--executor-cores,2,\
--conf,spark.dynamicAllocation.enabled=true,\
--conf,spark.yarn.executor.memoryOverhead=800,\
--conf,spark.yarn.driver.memoryOverhead=800,\
s3://geotrellis-test/emr/eac/ingest-assembly-0.1.0.jar,\
--layerName,"japan-typhoon",\
--bbox,\"135.35,33.23,143.01,41.1\",\
--startDate,2015-07-01,\
--endDate,2015-11-30,\
--maxCloudCoverage,20.0,\
--limit,1,\
--output,accumulo,\
--params,\"instance=accumulo,table=tiles,user=root,password=secret\"\
] | cut -f2 | tee last-step-id.txt
s-324HJ2BMVD5
```

When developing it is prudent to make the first job you run limited in its input so you can exercise the whole processing chain as quickly as possible. This is what we do here by providing the `LIMIT` variable to the `make` command.

What basically happened here is that we instructed EMR to en-queue `spark-submit` command as job step, which will be executed when the cluster has sufficient resources available. `spark-submit` actually has two argument lists: Arguments before the application jar are configuration for Spark and YARN. Arguments after the application jar will be passed to our application to be parsed.

We could have also overwritten some of the query parameters here and issued the command as:

```console
❯ make START_DATE=2015-06-01 END_DATE=2015-06-15 BBOX=135.35,33.23,143.01,41.1 ingest
```

Another thing worth noting is the double escaping that is happening here. We must separate the step arguments by commas, which will be parsed and converted to spaces before they are executed on the cluster. You can read more about the required formats by running `aws emr add-steps help`.


#### Debugging and Monitoring

We need a way to monitor our job, inspect the logs, and see logs for previous jobs. Fortunately EMR provides us with those tools, we just need to connect to ports which are not open to the internet by default. We need to establish SSH SOCKS proxy.

```console
❯ make proxy

aws emr socks --cluster-id j-2L3HJ8N2BMVDV --key-pair-file "/Users/eugene/geotrellis-emr.pem"
ssh -o StrictHostKeyChecking=no -o ServerAliveInterval=10 -ND 8157 -i /Users/eugene/geotrellis-emr.pem hadoop@ec2-54-226-8-111.compute-1.amazonaws.com
```

Now we just need a way to configure our browsers to use the proxy for EC2 addresses. The easiest way to do that is to use Chrome and Foxy Proxy extension. Detailed instructions are [provided by Amazon](https://docs.aws.amazon.com/ElasticMapReduce/latest/ManagementGuide/emr-connect-master-node-proxy.html)

Once the proxy is configured we should be able to go to the EMR web console and see the links for `Resource Manager`, `Spark History Server`, and other status pages.

Likewise now that we have a SOCKS proxy available we should be able to browse to the master address and see our JavaScript app. You will see the master address both in the output  for `make proxy` command and in the EMR web console.


If we need to connect to our cluster master to do a little poking we can:

```console
❯ make ssh
aws emr ssh --cluster-id j-2L3HJ8N2BMVDV --key-pair-file "/Users/eugene/geotrellis-emr.pem"
```

#### Terminate Cluster

When you're done you can either use the AWS web console to terminate the cluster or:

```console
❯ make terminate-cluster
aws emr terminate-clusters --cluster-ids j-2L3HJ8N2BMVDV
```

# Deployment

It is important to emphasize that GeoTrellis is a library and as such does not hold any opinions on deployment by itself. It is rather the nature of the application using GeoTrellis that dictates what is an appropriate deployment strategy. For instance we can imagine two different ways in which this demo application could be deployed:

_Workspace Cluster_

In this manner a user would bring up an EMR cluster that would serve as their workspace. Likely he would trigger one or more ingests that would bring relevant information into the instance of Accumulo running on EMR and utilize the quick query/response afforded by Accumulo to perform his analysis. Once the analysis is complete the user chooses when to terminate the cluster.

In this manner we reap the benefits of scalability and isolation. From scalability perspective the size of the cluster and its existence is closely tied to the work required by a single user, once the analysis is done there is no further expense. From isolation standpoint the cluster encapsulates nearly all of the environment required to support the application. If there is a fault, other users are unaffected, multiple users would be able to run different versions of the application without conflict, there is no resource contention between multiple instance of the cluster etc.

Importantly the user interaction involves triggering spark ingest jobs which may consume cluster resources for long periods of time. However this process is intuitive to the user since they represent the work they requested.

_Ingest/Serve Cluster_

A second way such application could be deployed is as a long-lived cluster that serves some view of the data. In this case the ingest step would be run once during the cluster setup stage and then optionally periodically to refresh the data. Then user interaction could happen at any point later in form of tile service requests that are satisfied through key/value lookups in Accumulo.

It is important to note that in this scenario the Spark ingest is not triggered by the user interactions and in fact a spark context is not required to satisfy user requests. After the initial job we are in fact treating ERM cluster as an Accumulo backed tile service to satisfy user requests. Because Spark context is not required to satisfy user requests the requests are quite lite and we can feel safer about sharing this resource amongst many users.


## Jenkins

To build and deploy the demo from Jenkins we can use the same `Makefile`.
We can need to define Jenkins job parameters to match the environment variables used in the `Makefile` and then build targets it with the `-e` parameter to allow the environment variables to overwrite the file defaults.

Since our scripts rely on AWS CLI we must use the Jenkins credentials plugin to define `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` for the build environment.

#### Building

```console
# Requires: S3_URI
# Build and upload assemblies with scripts
make -e upload-code || exit 1
```

#### Deploying

```console
# Requires: S3_URI, EC2_KEY, START_DATE, END_DATE, BBOX, WORKER_COUNT

make -e create-cluster || exit 1
make -e ingest || (make -e terminate-cluster && exit 1)
make -e wait || (make -e terminate-cluster && exit 1)
```

Included `Jenkinsfile` shows how we can use Jenkins DSL to build a job that deploys a an EMR cluster, monitors the ingest step and waits for user input to optionally terminate the cluster when the ingest step is done.

Also note that after the cluster has been successfully initialized we need to check for success of subsequent steps and tare down the cluster on failure to avoid producing an orphan cluster from a failed job.
