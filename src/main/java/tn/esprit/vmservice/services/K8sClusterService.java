package tn.esprit.vmservice.services;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class K8sClusterService {

    public String createK8sCluster(String username, String clusterName, int workerCount, String osType, String size) {
        // Exemple : on crée 1 master et N workers avec des YAML templates
        try {
            String templatePath = "src/main/resources/templates/";

            String outputPath = "/app/generated/";

            // Génération des fichiers YAML à partir de modèles (tu peux utiliser String.replace() pour injecter les noms, ports…)
            for (int i = 0; i < workerCount + 1; i++) {
                String role = (i == 0) ? "master" : "worker" + i;
                String yaml = Files.readString(Paths.get(templatePath + "vm-template.yaml"))
                        .replace("{{CLUSTER_NAME}}", clusterName)
                        .replace("{{ROLE}}", role)
                        .replace("{{USERNAME}}", username)
                        .replace("{{IMAGE}}", "ubuntu:20.04")  // À adapter selon osType
                        .replace("{{CPU}}", "500m")
                        .replace("{{MEMORY}}", "512Mi");

                Files.writeString(Paths.get(outputPath + clusterName + "-" + role + ".yaml"), yaml);

                // Appliquer le YAML avec kubectl
                new ProcessBuilder("kubectl", "apply", "-f", outputPath + clusterName + "-" + role + ".yaml").start().waitFor();
            }

            return "Cluster K8s créé pour l'utilisateur " + username;
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }
}
