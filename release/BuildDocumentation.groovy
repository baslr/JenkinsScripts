#!groovy
REGISTRY="192.168.0.1"
REGISTRY_URL="https://${REGISTRY}/"

DOCKER_CONTAINER=[:]
RELEASE_OUT_DIR="/net/fileserver/"
LOCAL_TAR_DIR="/mnt/workspace/tmp/"
branches = [:]
failures = ""
ADMIN_ACCOUNT = "release-bot@arangodb.com"
lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"
lastKnownGitRev = ""
currentGitRev = ""
WORKSPACE = ""
BUILT_FILE = ""
DIST_FILE = ""
fatalError = false
VERBOSE = true
testParams = [:]
def CONTAINERS=[
  //  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centosix',            'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/'],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centoseven',          'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'opensusethirteen',    'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'debianjessie',        'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'native', 'testType': 'docker', 'name': 'debianjessieDocu',    'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntutwelveofour',   'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': false],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntufourteenofour', 'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntusixteenofour',  'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'native', 'testType': 'native', 'name': 'windows',             'packageFormat': 'NSIS',   'OS': "Windows", 'buildArgs': "--msvc",     'cluster': false, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'native', 'testType': 'native', 'name': 'macos',               'packageFormat': 'Bundle', 'OS': "Darwin",  'buildArgs': "--clang",    'cluster': false, 'LOCALFS': '/Users/jenkins/mnt/workspace/tmp/', 'FS': '/Users/jenkins/net/fileserver/', 'reliable': true],
]

if (preferBuilder.size() > 0) {
  for (int c  = 0; c < CONTAINERS.size(); c++) {
    if (CONTAINERS[c]['name'] == preferBuilder) {
      print("prefering: ${CONTAINERS[c]}")
      DOCKER_CONTAINER = CONTAINERS[c]
      RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
      LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
    }
  }
} else {
  for (int c  = 0; c < CONTAINERS.size(); c++) {
    if (CONTAINERS[c]['buildType'] == 'docker') {
      DOCKER_CONTAINER = CONTAINERS[c]
      RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
      LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
    }
  }
}

OS = DOCKER_CONTAINER['OS']


def getReleaseOutDir(String enterpriseUrl, String jobname) {
  if (enterpriseUrl.size() > 10) {
    outDir = "${RELEASE_OUT_DIR}/EP/${jobname}"
  } else {
    outDir = "${RELEASE_OUT_DIR}/CO/${jobname}"
  }
  return outDir
}

def compileSource(buildEnv, Boolean buildUnittestTarball, String enterpriseUrl, String outDir, String envName) {
  try {
    if (!buildUnittestTarball) {
      outDir = getReleaseOutDir(enterpriseUrl, envName)
    }
    
    echo "building cookbook: "
    sh "cd Cookbook; ./build.sh; cd .."
    print(buildEnv)
    def BUILDSCRIPT = "cd Documentation/Books; make build-dist-books OUTPUT_DIR=${outDir} COOKBOOK_DIR=../../Cookbook/cookbook/"
    sh BUILDSCRIPT
    
    if (VERBOSE) {
      sh "ls -l ${outDir}"
    }
  } catch (err) {
    stage('Send Notification for failed build' ) {
      gitCommitter = sh(returnStdout: true, script: 'git --no-pager show -s --format="%ae"')

      mail (to: gitCommitter,
            subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB Documentation' failed to run.", 
            body: err.getMessage());
      currentBuild.result = 'FAILURE'
      throw(err)
    }
  }
}

def setupEnvCompileSource(buildEnvironment, Boolean buildUnittestTarball, String enterpriseUrl) {
  def outDir = ""
  print(buildEnvironment)
  if (buildEnvironment['buildType'] == 'docker') {
    node('docker') {
      docker.withRegistry(REGISTRY_URL, '') {
        def myBuildImage = docker.image("${buildEnvironment['name']}/build")
        myBuildImage.pull()
        echo "hello before docker"
        docker.image(myBuildImage.imageName()).inside("--volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw --volume /jenkins:/mnt/:rw ") {
          echo "hello from docker"
          if (VERBOSE) {
            sh "mount"
            sh "pwd"
            sh "cat /etc/issue /mnt/workspace/issue /etc/passwd"
            
          }
        
          sh 'pwd > workspace.loc'
          WORKSPACE = readFile('workspace.loc').trim()
          echo "hi"
          echo "${WORKSPACE}/out"
          outDir = "${WORKSPACE}/out"
          echo "checking out: "
          compileSource(buildEnvironment, buildUnittestTarball, enterpriseUrl, outDir, buildEnvironment['name'])
        }
      }
    }
  }
  else {
    print("building native")
    node(buildEnvironment['name']){
      print "else:"
      echo "building on ${buildEnvironment['name']}"
      sh 'pwd > workspace.loc'
      WORKSPACE = readFile('workspace.loc').trim()
      outDir = "${WORKSPACE}/out"
      compileSource(buildEnvironment, buildUnittestTarball, enterpriseUrl, outDir, buildEnvironment['name'])
    }
  }
}


stage("cloning source")
if (DOCKER_CONTAINER['buildType'] == 'docker') {
  node('docker') {
    if (VERBOSE) {
      sh "pwd"
      sh "cat /etc/issue /jenkins/workspace/issue"
    }
    if (fileExists(lastKnownGoodGitFile)) {
      lastKnownGitRev=readFile(lastKnownGoodGitFile)
    }
    sh "rm -f 3rdParty/rocksdb/rocksdb/util/build_version.cc"
    checkout([$class: 'GitSCM',
              branches: [[name: "devel"]],
              doGenerateSubmoduleConfigurations: false,
              extensions: [[$class: 'SubmoduleOption',
                            disableSubmodules: false,
                            parentCredentials: false,
                            recursiveSubmodules: true,
                            reference: '',
                            trackingSubmodules: false]],
              submoduleCfg: [],
              userRemoteConfigs:
              [[url: 'https://github.com/arangodb/arangodb.git']]])
    // git url: 'https://github.com/arangodb/arangodb.git', tag: "${GITTAG}"
    currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    // follow deletion of upstream tags:
    sh "git fetch --prune origin +refs/tags/*:refs/tags/*"

    // if (FORCE_GITBRANCH != "") {
    //   sh "git checkout ${FORCE_GITBRANCH}; git pull --all"
    //   sh 'echo "${GITTAG}" | sed "s;^v;;" > VERSION'
    // } else {
    //   sh "git checkout ${GITTAG}"
    // }
    sh "mkdir -p Cookbook"
    dir ('Cookbook') {
      checkout([$class: 'GitSCM',
                branches: [[name:"master"]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [[$class: 'SubmoduleOption',
                              disableSubmodules: false,
                              parentCredentials: false,
                              recursiveSubmodules: true,
                              reference: '',
                              trackingSubmodules: false]],
                submoduleCfg: [],
                userRemoteConfigs:
                [[url: 'https://github.com/arangodb/Cookbook.git']]])
    }
    print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
  }
}
else {
  node(DOCKER_CONTAINER['name']) {
    if (VERBOSE) {
      sh "pwd"
      sh "uname -a"
    }
    if (fileExists(lastKnownGoodGitFile)) {
      lastKnownGitRev=readFile(lastKnownGoodGitFile)
    }
    sh "rm -f 3rdParty/rocksdb/rocksdb/util/build_version.cc"
    // git url: 'https://github.com/arangodb/arangodb.git', tag: "${GITTAG}"
    checkout([$class: 'GitSCM',
              branches: [[name: "${GITTAG}"]],
              doGenerateSubmoduleConfigurations: false,
              extensions: [[$class: 'SubmoduleOption',
                            disableSubmodules: false,
                            parentCredentials: false,
                            recursiveSubmodules: true,
                            reference: '',
                            trackingSubmodules: false]],
              submoduleCfg: [],
              userRemoteConfigs:
              [[url: 'https://github.com/arangodb/arangodb.git']]])
    // follow deletion of upstream tags:
    sh "git fetch --prune origin +refs/tags/*:refs/tags/*"
    currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    // if (FORCE_GITBRANCH != "") {
    //   sh "git checkout ${FORCE_GITBRANCH}; git pull --all"
    //   sh 'echo "${GITTAG}" | sed "s;^v;;" > VERSION'
    // } else {
    //   sh "git checkout ${GITTAG}"
    // }
    sh "mkdir -p Cookbook"
    dir ('Cookbook') {
      checkout([$class: 'GitSCM',
                branches: [[name:"master"]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [[$class: 'SubmoduleOption',
                              disableSubmodules: false,
                              parentCredentials: false,
                              recursiveSubmodules: true,
                              reference: '',
                              trackingSubmodules: false]],
                submoduleCfg: [],
                userRemoteConfigs:
                [[url: 'https://github.com/arangodb/Cookbook.git']]])
    }
    print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
  }
}
  
stage("building ArangoDB")
try {
  if (preferBuilder.size() > 0) {
    print(DOCKER_CONTAINER)
    setupEnvCompileSource(DOCKER_CONTAINER, false, ENTERPRISE_URL)
  }
  else {
    for (int c  = 0; c < CONTAINERS.size(); c++) {
      if (CONTAINERS[c]['buildType'] == 'docker' && CONTAINERS[c]['reliable'] == true) {
        DOCKER_CONTAINER = CONTAINERS[c]
        RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
        LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
        OS = DOCKER_CONTAINER['OS']
        print(DOCKER_CONTAINER)
        setupEnvCompileSource(DOCKER_CONTAINER, false, ENTERPRISE_URL)
      }
    }
  }
} catch (err) {
  stage('Send Notification for build' )
  mail (to: ADMIN_ACCOUNT, 
        subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB' has had a FATAL error.", 
        body: err.getMessage());
  currentBuild.result = 'FAILURE'
  throw(err)
}


/*
stage("generating release build report")
if (DOCKER_CONTAINER['buildType'] == 'docker') {
  nodeName = 'docker'
} else {
  nodeName = DOCKER_CONTAINER['name']
}
node(nodeName) {
      
  def subject = "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) is finished"
      
  mail (to: 'release-bot@arangodb.com',
        subject: subject,
        body: "we successfully compiled ${GITTAG} \nfind the results at ${env.BUILD_URL}.");
}

*/
