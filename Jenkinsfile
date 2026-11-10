pipeline {
    agent any

    tools {
        // Names must match Manage Jenkins -> Tools
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }

    environment {
        APP_NAME        = 'agentic-supply-chain'
        DOCKER_REPO     = 'your-dockerhub-username/agentic-supply-chain'
        IMAGE_TAG       = "${env.BUILD_NUMBER}"
        DOCKER_CREDS_ID = 'dockerhub-creds'
        KUBECONFIG_ID   = 'kubeconfig'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn -B clean verify -Dspring.profiles.active=h2'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${DOCKER_REPO}:${IMAGE_TAG} -t ${DOCKER_REPO}:latest ."
            }
        }

        stage('Docker Push') {
            when { branch 'main' }
            steps {
                withCredentials([usernamePassword(credentialsId: DOCKER_CREDS_ID,
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push ${DOCKER_REPO}:${IMAGE_TAG}
                        docker push ${DOCKER_REPO}:latest
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when { branch 'main' }
            steps {
                withCredentials([file(credentialsId: KUBECONFIG_ID, variable: 'KUBECONFIG')]) {
                    sh '''
                        kubectl apply -f k8s/
                        kubectl set image deployment/${APP_NAME} ${APP_NAME}=${DOCKER_REPO}:${IMAGE_TAG}
                        kubectl rollout status deployment/${APP_NAME} --timeout=120s
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
            cleanWs()
        }
        success {
            echo "Build ${env.BUILD_NUMBER} succeeded"
        }
        failure {
            echo "Build ${env.BUILD_NUMBER} failed"
        }
    }
}
