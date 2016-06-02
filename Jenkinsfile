/* Required Parameters:
 * KEY_NAME - EMR SSH key
 * EMR_TARGET - S3 URI that contains assembly and scripts
 * BBOX - xmin,ymin,xmax,ymax bounding box for query
 * WORKER_COUNT - number of workers to spin up
 */

env.KEY_NAME = KEY_NAME
env.EMR_TARGET = EMR_TARGET
env.BBOX = BBOX
env.WORKER_COUNT = WORKER_COUNT

node {
  withCredentials(
    [[$class: 'UsernamePasswordMultiBinding',
      credentialsId: CREDENTIALS,
      usernameVariable: 'AWS_ACCESS_KEY_ID',
      passwordVariable: 'AWS_SECRET_ACCESS_KEY'
    ]])
  {
    stage "Launch"
    sh "make -e create-cluster"
    sh "make -e start-ingest"

    stage "Wait"
    sh "make -e wait-for-step"

    stage "Cleanup"
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
    if (userIn['TERMINATE_CLUSTER']) { sh "make -e terminate-cluster" }
  }
}
