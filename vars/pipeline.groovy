def call(Map config = [:]) {
    pipeline {
        agent any
        environment {
            SCANNER_HOME = tool 'sonar-scanner'
        }
        stages {
            stage('Clean Workspace') {
                steps {
                    cleanWs()
                }
            }
            stage('Checkout from Git') {
                steps {
                    git branch: config.branch ?: 'main', url: config.repoUrl ?: 'https://github.com/MohamedNour95/nextflix.git'
                }
            }
            stage("SonarQube Analysis") {
                steps {
                    withSonarQubeEnv(config.sonarServer ?: 'sonar-server') {
                        sh """$SCANNER_HOME/bin/sonar-scanner \
                        -Dsonar.projectName=${config.projectName ?: 'Netflix'} \
                        -Dsonar.projectKey=${config.projectKey ?: 'Netflix'}"""
                    }
                }
            }
            stage('OWASP FS Scan') {
                steps {
                    dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: 'OWASP DP-Check'
                    dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
                }
            }
            stage('Trivy FS Scan') {
                steps {
                    script {
                        try {
                            sh "trivy fs . > trivyfs.txt"
                        } catch(Exception e) {
                            input(message: "Trivy FS Scan failed. Are you sure to proceed?", ok: "Proceed")
                        }
                    }
                }
            }
            stage("Docker Build Image") {
                steps {
                    sh "docker build --build-arg API_KEY=${config.apiKey} -t ${config.imageName ?: 'netflix'} ."
                }
            }
            stage("Trivy Image Scan") {
                steps {
                    sh "trivy image ${config.imageName ?: 'netflix'} > trivyimage.txt"
                    script {
                        input(message: "Trivy Image Scan completed. Are you sure to proceed?", ok: "Proceed")
                    }
                }
            }
            stage("Docker Push") {
                steps {
                    script {
                        withDockerRegistry([credentialsId: config.dockerCredentialsId ?: 'docker-cred', url: '']) {   
                            sh "docker tag ${config.imageName ?: 'netflix'} ${config.dockerRepo ?: 'mohamednour95/netflix'}:latest"
                            sh "docker push ${config.dockerRepo ?: 'mohamednour95/netflix'}:latest"
                        }
                    }
                }
            }
        }
    }
}
