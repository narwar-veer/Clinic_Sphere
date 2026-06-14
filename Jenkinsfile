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
                bat 'mvn -B clean test package'
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Build Docker Image') {
            steps {
                bat 'docker build -t %IMAGE_NAME% .'
            }
        }

        stage('Run Docker Container') {
            steps {
                bat '''
                docker rm -f clinic-booking-test >nul 2>&1
                docker run -d --name clinic-booking-test -p 8082:8082 %IMAGE_NAME%
                timeout /t 20 /nobreak
                '''
            }
        }

        stage('Smoke Test') {
            steps {
                bat 'curl --fail http://localhost:8082/actuator/health'
            }
        }

        stage('Cleanup') {
            steps {
                bat 'docker rm -f clinic-booking-test >nul 2>&1'
            }
        }
    }

    post {
        always {
            bat 'docker rm -f clinic-booking-test >nul 2>&1'
        }
    }
}