# GeoTrellis Landsat EMR demo

This project is a demo of using GeoTrellis to ingest a set of Landsat scenes from S3 into an AWS EMR instance running Apache Accumulo and stand up tile sever that is serves the Landsat multi-band tiles as either RGB, NDVI, or NDWI layers. In addition it provides a way a "diff" view between layers, scenes captured at different time, for either NDWI or NDVI.

## EMR Cluster

We're going to standup an EMR cluster with Apache Accumulo, which we will use to store and serve the Landsat tiles.

### Configuration

Before attempting to start a cluster you should edit `scripts/environmet.sh` to update the key and S3 URL that will be used for this demo:

```sh
EMR_TARGET=s3://<your-bucket>/<demo-prefix>
KEY_NAME=<your-ec2-key>
```

The rest of the settings in the file configure the machines to be used in the cluster. They have been tuned for the size if this particular demo ingest. Worker count will need to be increased if you're going to attempt a larger dataset.

### Build Projects

In order to submit either the ingest or the tile server task to spark we must generate a "fat jar" or assembly, which will contain the program including all its transitive dependencies, excluding spark and hadoop, which will already be present in the JVM invoking the assembly.

```console
./sbt "project ingest" assembly
./sbt "project server" assembly
```

### Upload Code to S3

Before we create the cluster we need to upload some scripts to `$EMR_TARGET`. Spark on EMR is going to be pulling these down when the cluster starts.

```sh
scripts/upload-code.sh
```

This is going to upload the following files for the following reasons:
 - `bootstrap-geowave.sh`: Bootstrap script that installs Accumulo and GeoWave on EMR
 - `geowave-install-lib.sh`: Dependency of the above bootstrap script
 - `wait-for-accumulo.sh`: Script that will wait for Accumulo initialization to complete
 - `tile-server.sh`: Script that will start GeoTrellis tile server in the background
 - `ingest-assembly-0.1.0.jar`: Fat jar containing ingest program classes and their dependencies
 - `server-assembly-0.1.0.jar`: Fat jar containing tile server classes and their dependencies


### Create Cluster

```sh
scripts/create-cluster.sh
```

This will report new cluster ID, we'll need it later.

```json
{
    "ClusterId": "j-XXXXXXXXXXXXX"
}
```

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

For the ingest step we're going to require a GeoJSON file containing a `Polygon` covering area of interest like `polygon.json`:

```json
{
"type": "Polygon",
"coordinates": [
  [
    [
      -77.552490234375,
      38.57393751557591
    ],
    [
      -77.552490234375,
      40.93841495689793
    ],
    [
      -73.751220703125,
      40.93841495689793
    ],
    [
      -73.751220703125,
      38.57393751557591
    ],
    [
      -77.552490234375,
      38.57393751557591
    ]
  ]
]
}
```

```sh
scripts/start-ingest.sh --cluster-id=j-XXXXXXXXXXXXX --polygon=scripts/polygon.json
```

This script will copy the polygon to the `$EMR_TARGET` and kick off a spark process that will contact Development Seed Landsat8 Metadata API service to get a listing of Landsat scenes matching the query parameters. Not all scenes are present on S3 `landsat-pdt` bucket so they first the listing must be filtered only to scenes that exist.

The bands of interest are hard-coded to R,G,B,IR,QA and will be combined for each scene into a single `MultibandTile`, these tiles will be tiled to TMS layout and saved to Accumulo.

You can view the progress of this process by browsing to `Resource Manager` > `Application Manager` from the AWS console cluster detail page.

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


## Makefile

Included is a clever `Makefile` that will hopefully make playing with this demo easier, it can be used following this pattern:

```s
make upload-code
make create-cluster
make CLUSTER_ID=j-XXXXXXXXXXXXX POLYGON=scripts/polygon.json start-ingest
```

`create-cluster` and `start-ingest` targets will smartly invoke `upload-code` when needed which will build the project assemblies if needed.
