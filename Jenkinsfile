/* Required Parameters:
 * KEY_NAME - EMR SSH key
 * EMR_TARGET - S3 URI that contains assembly and scripts
 * BBOX - xmin,ymin,xmax,ymax bounding box for query
 * WORKER_COUNT - number of workers to spin up
 *
 * Jenkins environment is a mess:
 * The pipeline job parameters are given as groovy variables,
 * which may either be used in string interpolation or
 * assigned to env dictionary to be exposed as environment
 * variables in the shell command.
 *
 * This is on contracts to a freeform job where they job parameters
 * would come in as shell environment variables directly.
 */

env.KEY_NAME = KEY_NAME
env.EMR_TARGET = EMR_TARGET
env.BBOX = BBOX
env.START_DATE = START_DATE
env.END_DATE = END_DATE
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
    sh "make -e create-cluster" || exit 1
    sh "make -e start-ingest" || exit 1

    stage "Wait"
    sh "make -e wait-for-step"

    stage "Cleanup"
    def terminate = input (id: 'Cluster Cleanup',
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
    if (terminate) { sh "make -e terminate-cluster" }
  }
}
