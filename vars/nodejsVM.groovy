def call(Map configMap){
pipeline {
    agent {
        node {
            label 'AGENT-1'
        }
    }
    environment {
        packageVersion = ''
        environment = ''
        nexusURL = '172.31.37.6:8081'
    }
    options {
        ansiColor('xterm')
        disableConcurrentBuilds()
    }
    parameters {
        booleanParam(name: 'deploy', defaultValue: 'false', description: 'Enable to deploy catalogue')
    }
    stages {
        stage('Get the package version') {
            steps {
                script {
                def packageJSON = readJSON file: 'package.json'
                packageVersion = packageJSON.version
                echo "Application Version: $packageVersion"
                }
            }
        }
        stage('Installing NodeJS') {
            steps{
                sh """
                    sudo dnf module disable nodejs -y
                    sudo dnf module enable nodejs:20 -y
                    sudo dnf install nodejs -y
                    sudo dnf install zip -y
                """
            }
        }
        stage('Installing Dependencies') {
            steps {
                sh """
                    npm install
                """
            }
        }
        stage('Running Unit tests') {
            steps {
                echo 'Unit tests will run here'
            }
        }
        stage('SonarQube Scanning') {
            steps {
                sh """
                    sonar-scanner
                """
            }
        }
        stage('Zipping the Artifact') {
            steps {
                sh """
                    zip -q -r catalogue.zip ./* -x ".git" -x "*.zip"
                """
            }
        }
        stage('Uploading the Artifact') {
            steps {
                nexusArtifactUploader(
                    nexusVersion: 'nexus3',
                    protocol: 'http',
                    nexusUrl: "${nexusURL}",
                    groupId: 'com.roboshop',
                    version: "${packageVersion}",
                    repository: 'catalogue',
                    credentialsId: 'nexus-auth',
                    artifacts: [
                        [artifactId: 'catalogue',
                        classifier: '',
                        file: 'catalogue.zip',
                        type: 'zip']
                    ]
                )
            }
        }
        stage('Build Job: catalogue-deploy') {
            when {
                // expression {params.deploy == true}
                expression {params.deploy}
            }
            steps {
                build job: '../catalogue-deploy', wait: true,
                parameters: [
                    string(name: 'version', value: "${packageVersion}"),
                    string(name: 'environment', value: 'dev')
                ]
            }
                    
            }
        }
    post { 
        always { 
            echo 'Deleting the directory'
            deleteDir()
        }
        failure {
            echo 'The pipeline is Failed, Please send some alerts'
        }
        success {
            echo 'Pipeline executed successfully'
        }
    }
}
}