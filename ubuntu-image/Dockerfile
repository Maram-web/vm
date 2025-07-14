FROM ubuntu:22.04

# Installer SSH + kubectl
RUN apt-get update && \
    apt-get install -y openssh-server curl && \
    curl -LO "https://dl.k8s.io/release/v1.30.1/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && mv kubectl /usr/local/bin/kubectl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
CMD ["tail", "-f", "/dev/null"]