def call(Map pipelineParams) {
    pipeline{
        agent any

        environment {
        SCANNER_HOME = tool "${pipelineParams.sonarTool}"
        }

        parameters {
            booleanParam(name: 'SKIP_SONAR', defaultValue: true, description: 'Skip SonarQube analysis ?')
            booleanParam(name: 'SKIP_OWASP_FS_SCAN', defaultValue: true, description: 'Skip OWASP FS Scan ?')
            booleanParam(name: 'SKIP_TRIVY_FS_SCAN', defaultValue: true, description: 'Skip Trivy FS Scan ?')
            booleanParam(name: 'SKIP_TRIVY_IMAGE_SCAN', defaultValue: true, description: 'Skip Trivy Image Scan ?')
        }

        stages{
            // stage('Clean Workspace') {
            //     steps {
            //         cleanWs()
            //     }
            // }

            stage('Checkout from git') {
                steps{
                    git branch: pipelineParams.branch, credentialsId: pipelineParams.scmCred, url: pipelineParams.scmURL
                }
            }

            stage('SonarQube analysis') {
                steps {
                    script{
                        if (params.SKIP_SONAR) {
                            echo "SonarQube test skip is $SKIP_SONAR"
                        } 
                        else {
                            withSonarQubeEnv("${pipelineParams.sonarServer}") {
                                sh """$SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=${pipelineParams.sonarProjectName} \
                                -Dsonar.projectKey=${pipelineParams.sonarProjectkey}"""
                            }
                        }
                    }
                }
            }

            stage('OWASP fs scan') {
                steps {
                    script{
                        if (params.SKIP_OWASP_FS_SCAN) {
                            echo "SonarQube test skip is $SKIP_OWASP_FS_SCAN"
                        } 
                        else {
                            dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: "${pipelineParams.owaspInstallation}"
                            dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
                        }
                    }
                }
            }

            stage('Trivy fs scan') {
                steps {
                    script {
                        if (params.SKIP_TRIVY_FS_SCAN) {
                            echo "SonarQube test skip is $SKIP_TRIVY_FS_SCAN"
                        }
                        else {
                            try {
                                sh "trivy fs . > trivyfs.txt"
                            } catch(Exception e) {
                                input(message: "Trivy FS Scan failed. Are you sure to proceed?", ok: "Proceed")
                            }
                        }
                    }
                }
            }

            stage('Docker build image') {
                steps {
                    sh "docker build --build-arg ${pipelineParams.buildArg} -t ${pipelineParams.imageName}:${pipelineParams.imageTag} ."
                }
            }

            stage('Trivy image scan') {
                steps {
                    script {
                        if (params.SKIP_TRIVY_IMAGE_SCAN) {
                            echo "SonarQube test skip is $SKIP_TRIVY_IMAGE_SCAN"
                        }
                        else {
                            sh "trivy image ${pipelineParams.imageName}:${pipelineParams.imageTag} > trivyimage.txt"
                            script{
                                input(message: "Are you sure to proceed?", ok: "Proceed")
                            }
                        }
                    }
                }
            }

            stage('Docker push image') {
                steps{
                    script {
                        withDockerRegistry(credentialsId: "${pipelineParams.dockerCred}", toolName: "${pipelineParams.dockerTool}"){   
                        sh "docker push ${pipelineParams.imageName}:${pipelineParams.imageTag}"
                        }
                    }
                }
            }

        }
    }
}