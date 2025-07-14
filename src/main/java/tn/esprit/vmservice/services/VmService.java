package tn.esprit.vmservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.repositories.VmInstanceRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class VmService {

    private final VmInstanceRepository vmInstanceRepository;
    private final YamlGeneratorService yamlGeneratorService;

    public String createTrainingVm(VmRequest request) {
        Map<String, String> nodeToIp = Map.of(
                "ceph2", "192.168.13.22",
                "ceph3", "192.168.13.33",
                "ceph4", "192.168.13.44"
        );

        String vmName = request.getVmName();
        String username = request.getUsername();

        // Enregistrer la VM
        VmInstance vm = new VmInstance();
        vm.setUsername(username);
        vm.setVmName(vmName);
        vm.setStorageType(request.getStorageType() != null ? request.getStorageType() : "RBD");
        vm.setStatus("CREATED");
        vm.setCreatedAt(LocalDateTime.now());
        vm.setSize(request.getSize());       // 👈 nouveau
        vm.setOsType(request.getOsType());   //
        vmInstanceRepository.save(vm);

        try {
            // 1️⃣ Générer le YAML
            String yamlContent = yamlGeneratorService.generateYaml(request);

            // 2️⃣ Appliquer via kubectl
            String kubectlOutput = applyYamlToCluster(yamlContent);

            // 3️⃣ Attendre que le pod démarre
            Thread.sleep(10000); // adapte si nécessaire

            // 4️⃣ Trouver le node + IP
            String nodeName = getNodeHostingPod(vmName);
            String ip = nodeToIp.get(nodeName);

            // 5️⃣ Commande test
            String output = executeCommand(ip, "springuser", "tonPassword", "echo Hello depuis " + vmName);

            // 6️⃣ Réponse finale
            return "✅ VM déployée avec succès :\n" + kubectlOutput + "\n\n💻 Commande test :\n" + output;

        } catch (Exception e) {
            return "❌ Erreur lors du déploiement : " + e.getMessage();
        }
    }


    public String getNodeHostingPod(String podName) throws Exception {
        Process process = Runtime.getRuntime().exec("kubectl get pod " + podName + " -n vm -o=jsonpath='{.spec.nodeName}'");
        process.waitFor();
        return new String(process.getInputStream().readAllBytes()).replace("'", "");
    }

    private String generateYaml(VmRequest request) {
        String yaml = """
        apiVersion: v1
        kind: Pod
        metadata:
          name: %s
          labels:
            app: %s
        spec:
          containers:
          - name: %s
            image: %s
            resources:
              requests:
                memory: "%sMi"
                cpu: "%s"
              limits:
                memory: "%sMi"
                cpu: "%s"
          restartPolicy: Never
        """;

        // Déduction des ressources selon size
        String memory, cpu;
        switch (request.getSize().toLowerCase()) {
            case "small" -> {
                memory = "256"; cpu = "0.5";
            }
            case "medium" -> {
                memory = "512"; cpu = "1";
            }
            case "large" -> {
                memory = "1024"; cpu = "2";
            }
            default -> {
                memory = "256"; cpu = "0.5";
            }
        }

        // Image selon osType
        String image = switch (request.getOsType().toLowerCase()) {
            case "ubuntu" -> "ubuntu:22.04";
            case "windows" -> "mcr.microsoft.com/windows/servercore:ltsc2022"; // À adapter selon ton infra
            default -> "ubuntu:22.04";
        };

        return String.format(yaml,
                request.getVmName(), request.getVmName(),
                request.getVmName(), image,
                memory, cpu, memory, cpu
        );
    }

    public String executeCommand(String ip, String username, String password, String command) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;

        try {
            session = jsch.getSession(username, ip, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10000); // 10 sec timeout

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null); // very important
            channel.setErrStream(System.err); // show errors

            InputStream in = channel.getInputStream();
            channel.connect();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[1024];
            int read;

            while (true) {
                while (in.available() > 0) {
                    read = in.read(buffer, 0, 1024);
                    if (read < 0) break;
                    output.append(new String(buffer, 0, read));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    break;
                }
                Thread.sleep(200);
            }

            return output.toString();

        } catch (JSchException e) {
            throw new RuntimeException("SSH connection failed: " + e.getMessage(), e);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    public String applyYamlToCluster(String yamlContent) throws IOException, InterruptedException {
        // 1. Sauvegarder dans un fichier temporaire
        File tempFile = File.createTempFile("vm-", ".yaml");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(yamlContent);
        }

        // 2. Lancer la commande kubectl apply
        ProcessBuilder processBuilder = new ProcessBuilder("/usr/bin/kubectl", "apply", "-f", tempFile.getAbsolutePath());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // 3. Lire la sortie
        StringBuilder output = new StringBuilder();
        try (InputStream in = process.getInputStream()) {
            int c;
            while ((c = in.read()) != -1) {
                output.append((char) c);
            }
        }

        int exitCode = process.waitFor();
        tempFile.delete(); // nettoyage

        if (exitCode != 0) {
            throw new RuntimeException("❌ Erreur lors du kubectl apply :\n" + output);
        }

        return output.toString();
    }




}