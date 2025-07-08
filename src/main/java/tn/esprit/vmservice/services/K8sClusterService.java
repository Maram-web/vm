package tn.esprit.vmservice.services;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class K8sClusterService {

    public String createK8sCluster(String username, String clusterName, int workerCount, String osType, String size) {
        try {
            // Charger le fichier template YAML depuis les ressources
            ClassPathResource resource = new ClassPathResource("templates/vm-template.yaml");
            String yamlTemplate = new String(resource.getInputStream().readAllBytes());

            // Créer un dossier temporaire local pour stocker les fichiers générés
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "generated-yamls");
            if (!tempDir.exists()) tempDir.mkdirs();

            // Générer master + N workers
            for (int i = 0; i <= workerCount; i++) {
                String role = (i == 0) ? "master" : "worker" + i;

                // Remplacer les variables dans le template
                String filledYaml = yamlTemplate
                        .replace("{{CLUSTER_NAME}}", clusterName)
                        .replace("{{ROLE}}", role)
                        .replace("{{USERNAME}}", username)
                        .replace("{{IMAGE}}", getImageForOs(osType))
                        .replace("{{CPU}}", getCpuForSize(size))
                        .replace("{{MEMORY}}", getMemoryForSize(size));

                // Chemin du fichier temporaire YAML
                Path outputPath = Path.of(tempDir.getAbsolutePath(), clusterName + "-" + role + ".yaml");
                Files.writeString(outputPath, filledYaml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // Appliquer le fichier avec kubectl
                Process process = new ProcessBuilder("kubectl", "apply", "-f", outputPath.toString()).start();
                process.waitFor();
            }

            return "✅ Cluster K8s créé avec succès pour l'utilisateur " + username;
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Erreur : " + e.getMessage();
        }
    }

    private String getImageForOs(String osType) {
        return osType.equalsIgnoreCase("ubuntu") ? "ubuntu:20.04" : "windows:latest"; // à adapter
    }

    private String getCpuForSize(String size) {
        return switch (size.toLowerCase()) {
            case "small" -> "500m";
            case "medium" -> "1000m";
            case "large" -> "2000m";
            default -> "500m";
        };
    }

    private String getMemoryForSize(String size) {
        return switch (size.toLowerCase()) {
            case "small" -> "512Mi";
            case "medium" -> "1024Mi";
            case "large" -> "2048Mi";
            default -> "512Mi";
        };
    }
}
