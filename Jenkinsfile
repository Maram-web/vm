pipeline {
    agent any

    environment {
        TIMESTAMP = "${new Date().format('yyyyMMdd-HHmmss')}"
        IMAGE_TAG = "v${TIMESTAMP}"
        IMAGE_NAME = "marammanai/vm-service:${IMAGE_TAG}"
        K8S_MASTER = "ceph1@192.168.13.11"
        DEPLOY_YAML = "k8s-vm-deployment.yaml"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'vm', url: 'https://github.com/Maram-web/vm.git'
            }
        }

        stage('Build & Push vm-service Image') {
            steps {
                sh "docker build -t $IMAGE_NAME ."
                withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push $IMAGE_NAME
                    '''
                }
            }
        }

        stage('Inject vm-service Tag into YAML') {
            steps {
                sh "sed 's|__IMAGE_TAG__|$IMAGE_TAG|g' k8s-vm-template.yaml > $DEPLOY_YAML"
            }
        }

        stage('Build & Push ubuntu-ssh-kubectl Image') {
            steps {
                script {
                    def TIMESTAMP2 = new Date().format('yyyyMMdd-HHmmss')
                    def UBUNTU_IMAGE_TAG = "v${TIMESTAMP2}"
                    def UBUNTU_IMAGE_NAME = "marammanai/ubuntu-ssh-kubectl:${UBUNTU_IMAGE_TAG}"
                    env.UBUNTU_IMAGE_TAG = UBUNTU_IMAGE_TAG

                    sh """
                        cd ubuntu-image
                        docker build -t ${UBUNTU_IMAGE_NAME} .
                        docker push ${UBUNTU_IMAGE_NAME}
                    """

                    // ✅ Inject Ubuntu image tag into YAML
                    sh "sed -i 's|__UBUNTU_IMAGE_TAG__|${UBUNTU_IMAGE_TAG}|g' $DEPLOY_YAML"

                    // ✅ Update or add ubuntu.image.tag in application.properties
                    sh """
                        if grep -q '^ubuntu.image.tag=' src/main/resources/application.properties; then
                            sed -i 's|^ubuntu.image.tag=.*|ubuntu.image.tag=${UBUNTU_IMAGE_TAG}|' src/main/resources/application.properties
                        else
                            echo 'ubuntu.image.tag=${UBUNTU_IMAGE_TAG}' >> src/main/resources/application.properties
                        fi
                    """
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                sh '''
                    ssh-keyscan -H 192.168.13.11 >> ~/.ssh/known_hosts
                    scp $DEPLOY_YAML $K8S_MASTER:/home/ceph1/
                    ssh $K8S_MASTER kubectl apply -f /home/ceph1/$DEPLOY_YAML
                '''
            }
        }
    }

    post {
        success {
            echo "✅ vm-service deployed with tag: ${IMAGE_TAG}"
        }
        failure {
            echo "❌ vm-service deployment failed"
        }
    }
}
