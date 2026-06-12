pipeline {
  agent any

  environment {
    APP_NAME = 'clinic-booking-backend'
    IMAGE_NAME = "${APP_NAME}:${env.BUILD_NUMBER}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build and Test') {
      steps {
        sh 'mvn -B clean test package'
      }
    }

    stage('Archive Artifact') {
      steps {
        archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
      }
    }

    stage('Build Docker Image') {
      steps {
        sh 'docker build -t ${IMAGE_NAME} .'
      }
    }

    stage('Run Docker Container') {
      steps {
        sh '''
          docker rm -f clinic-booking-test || true
          docker run -d --name clinic-booking-test -p 8082:8082 ${IMAGE_NAME}
          sleep 20
        '''
      }
    }

    stage('Smoke Test') {
      steps {
        sh 'curl --fail http://localhost:8082/actuator/health'
      }
    }

    stage('Cleanup') {
      steps {
        sh 'docker rm -f clinic-booking-test || true'
      }
    }
  }

  post {
    always {
      sh 'docker rm -f clinic-booking-test || true'
    }
  }
}
