pipeline {
agent any

environment {
    APP_NAME = 'clinic-booking-backend'
    IMAGE_NAME = 'clinic-booking-backend'
    MAVEN_HOME = 'C:\\Users\\hp\\tools\\apache-maven-3.9.9'
}

stages {

    stage('Checkout') {
        steps {
            checkout scm
        }
    }

    stage('Diagnostics') {
        steps {
            bat 'echo ===== JAVA ====='
            bat 'where java || echo Java not found'
            bat 'java -version'

            bat 'echo ===== MAVEN ====='
            bat 'dir "%MAVEN_HOME%\\bin"'
            bat '"%MAVEN_HOME%\\bin\\mvn.cmd" -v'

            bat 'echo ===== DOCKER ====='
            bat 'where docker || echo Docker not found'
            bat 'docker --version'
        }
    }

    stage('Build and Test') {
        steps {
            bat '"%MAVEN_HOME%\\bin\\mvn.cmd" -B clean test package'
        }
    }

    stage('Archive Artifact') {
        steps {
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
        }
    }

    stage('Build Docker Image') {
        steps {
            bat 'docker build -t %IMAGE_NAME%:%BUILD_NUMBER% .'
        }
    }

    stage('Run Docker Container') {
        steps {
            bat '''
            docker rm -f clinic-booking-test >nul 2>&1
            docker run -d --name clinic-booking-test -p 8082:8082 %IMAGE_NAME%:%BUILD_NUMBER%
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
            bat 'docker compose down >nul 2>&1'
        }
    }
}

post {
    always {
        bat 'docker compose down >nul 2>&1'
    }

    success {
        echo 'Build and Smoke Test completed successfully.'
    }

    failure {
        echo 'Build or Smoke Test failed. Check logs above.'
    }

    success {
        echo 'Build completed successfully.'
    }

    failure {
        echo 'Build failed. Check logs above.'
    }
}

}
