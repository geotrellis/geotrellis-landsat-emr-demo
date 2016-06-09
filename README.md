# GeoTrellis Landsat EMR demo

This project is a demo of using GeoTrellis to ingest a set of Landsat scenes from S3 into an AWS EMR instance running Apache Accumulo and stand up tile sever that is serves the Landsat multi-band tiles as either RGB, NDVI, or NDWI layers. In addition it provides a way a "diff" view between layers, scenes captured at different time, for either NDWI or NDVI.

## EMR Cluster

We're going to standup an EMR cluster with Apache Accumulo, which we will use to store and serve the Landsat tiles.

### Build Projects

In order to submit either the ingest or the tile server task to spark we must generate a "fat jar" or assembly, which will contain the program including all its transitive dependencies, excluding spark and hadoop, which will already be present in the JVM invoking the assembly.

```console
./sbt "project ingest" assembly
./sbt "project server" assembly
```

In reality to launch and setup a cluster we need a lot more support scrpits. In order to make this easier we use a `Makefile` and place the required scripts and configurations in the `scripts` folder.

### Configuration

There are lots of options you may need to tweak for the job. For instance you will certanly need to change the S3 bucket that will be used to hold the assemblies and the EC2 key to be used when creating cluster machines.

The `Makefile` has a section of variables it defines as a default at the top. It may look like this:

```make
export AWS_DEFAULT_REGION := us-east-1
export S3_URI := s3://geotrellis-test/emr
export EC2_KEY := geotrellis-cluster
export WORKER_COUNT := 1
export WORKER_INSTANCE := m3.2xlarge
export WORKER_PRICE := 0.15
export MASTER_INSTANCE := m3.xlarge
export MASTER_PRICE := 0.15
export BBOX := -98.77,36.12,-91.93,41.48
```

Because they are `export`ed they will be available to shell scripts called from the make targets. You may either change the file to adjust it for your input or you may use `-e` option to make, which would use any variables from your environmet to overwrite the ones defined in the `Makefile`, turning them into defaults.

### Upload Code

In order to run the project on EMR the assembly files and some ancillary scripts need to be uploaded to S3 buket named in `$S3_URI` such they are accessable from EMR cluster. This can be done directly using:

```console
make upload-code
```
This is going to upload the following files for the following reasons:
 - `bootstrap-geowave.sh`: Bootstrap script that installs Accumulo and GeoWave on EMR
 - `geowave-install-lib.sh`: Dependency of the above bootstrap script
 - `bootstrap-demo.sh`: Script that will start GeoTrellis tile server in the background
 - `wait-for-accumulo.sh`: Script that will wait for Accumulo initialization to complete
 - `ingest-assembly-0.1.0.jar`: Fat jar containing ingest program classes and their dependencies
 - `server-assembly-0.1.0.jar`: Fat jar containing tile server classes and their dependencies

As a side benefit this will re-build the assemblies if they are out of date or unavailable.

### Create Cluster

```sh
make create-cluster
```

This will report new cluster ID and save it in `cluster-id.txt`, we'll need it later.

Because Accumulo requires HDFS and Zookeeper to be present, which they are not in EMR 4.* bootstrap phase, the actual script will schedule to be executed later. This necessary trick makes it an asynchronous process that is not monitored by EMR. So we  don't submit a job before Accumulo is ready the first step that we submit is `WaitForAccumulo` script that will poll in a loop until Accumulo is available.

The second step is going to start our tile server. We need it to run on the master so it's going to be use `yarn-client` mode.

After these steps are completed you should be able to [setup ssh proxy](https://docs.aws.amazon.com/ElasticMapReduce/latest/ManagementGuide/emr-connect-master-node-proxy.html) to view the `Resource Manager` and see the `Demo Server` server running as a YARN application.

You should be able to use Chrome, after configuring foxy proxy, to view: `http://ec2-??-??-??-??.compute-1.amazonaws.com:8899/catalog`

```json
{
  "layers": []
}
```

Nothing to report yet!

### Ingest Step

For the ingest step we're going to require a bounding box to intersect with available scenes.

```sh
make START_DATE=2015-06-01 END_DATE=2015-06-15 start-ingest
```

Similarly either define, pass, or overwrite the `BBOX` variable in the `Makefile`.

This script will kick off a spark process that will contact Development Seed Landsat8 Metadata API service to get a listing of Landsat scenes matching the query parameters. Not all scenes are present on S3 `landsat-pdt` bucket so they first the listing must be filtered only to scenes that exist.

The bands of interest are hard-coded to R,G,B,IR,QA and will be combined for each scene into a single `MultibandTile`, these tiles will be tiled to TMS layout and saved to Accumulo.

You can view the progress of this process by browsing to `Resource Manager` > `Application Manager` from the AWS console cluster detail page.

This step will take some time to complete, especially if you factor in the time it takes to allocate and bootstrap a spot-priced cluster. So there is a `wait-for-step` target that will poll the EMR API very 60s and terminate when the step has finished.

### Terminate Cluster

Of course when you're done you can either use the AWS web consle to terminate the cluster or use:

```console
make terminate-cluster
```

## Viewer

The viewer can be started locally, install steps are only necessary the first time:

```
cd viewer
npm install
npm install --global nodemon
npm start
```

Open `http://localhost:3000` in Chrome with Foxy Proxy enabled and you will be able to use the proxy to access the tile service.
Enter `http://ec2-??-??-??-??.compute-1.amazonaws.com:8899` into the `Catalog` field and hit `Load`
