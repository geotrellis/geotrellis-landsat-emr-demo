/* Required Parameters:
 * KEY_NAME - EMR SSH key
 * EMR_TARGET - S3 URI that contains assembly and scripts
 * BBOX - xmin,ymin,xmax,ymax bounding box for query
 * WORKER_COUNT - number of workers to spin up
 */

node {
  stage "Pregame"
  sh 'echo $CRED'
  sh 'env'
  withCredentials(
    [[$class: 'UsernamePasswordMultiBinding',
      credentialsId: 'default',
      usernameVariable: 'AWS_ACCESS_KEY_ID',
      passwordVariable: 'AWS_SECRET_ACCESS_KEY'
    ]])
  {
    stage "Launch cluster"
    sh '''make -e create-cluster'''
    def clusterId = readFile("scripts/cluster-id.txt").trim()
    echo "Cluster ID: " + clusterId

    stage "Start Ingest"
    sh '''make -e start-ingest'''
    def stepId = readFile("scripts/last-step-id.txt").trim()
    echo "Step ID: " + stepId

    stage "Wait for Ingest"
    sh """make -e wait-for-step"""

    stage "Cluster Cleanup"
    def userIn = input (id: 'Cluster Cleanup',
          message: 'Cluster Cleanup',
          ok: 'Okay',
          parameters: [
          [
            $class: 'BooleanParameterDefinition',
            defaultValue: true,
            name: 'TERMINATE_CLUSTER',
            description: 'Finish jenkins job and terminate cluster'
          ]
        ])
    if (userIn['TERMINATE_CLUSTER']) { sh """make -e terminate-cluster""" }
  }
}
