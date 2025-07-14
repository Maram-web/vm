# Étape 1 : Base Ubuntu
FROM ubuntu:22.04

# Étape 2 : Mettre à jour et installer SSH, sudo, curl
RUN apt-get update && \
    apt-get install -y openssh-server sudo curl && \
    mkdir /var/run/sshd

# Étape 3 : Ajouter l’utilisateur ceph avec mot de passe et droits sudo
RUN useradd -m ceph && echo "ceph:maram" | chpasswd && adduser ceph sudo

# Étape 4 : Activer l’authentification par mot de passe + root login
RUN sed -i 's/#\?PasswordAuthentication .*/PasswordAuthentication yes/' /etc/ssh/sshd_config && \
    sed -i 's/#\?PermitRootLogin .*/PermitRootLogin yes/' /etc/ssh/sshd_config

# Étape 5 : Installer kubectl (v1.30.1)
ARG KUBECTL_VERSION=v1.30.1
RUN curl -LO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && mv kubectl /usr/local/bin/kubectl

# Exposer le port SSH
EXPOSE 22

# Commande de démarrage
CMD ["/usr/sbin/sshd", "-D"]
