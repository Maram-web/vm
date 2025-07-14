package tn.esprit.vmservice.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.repositories.VmInstanceRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VmService {
    private static final Logger log = LoggerFactory.getLogger(VmService.class);

    private final VmInstanceRepository vmInstanceRepository;
    private final YamlGeneratorService yamlGeneratorService;

    /**
     * Crée une VM d'entraînement selon la requête reçue.
     * Génère un manifeste YAML, l'applique via kubectl,
     * attend le démarrage, trouve le nœud, puis teste une connexion SSH.
     */
    public String createTrainingVm(VmRequest request) {
        log.info("Début de createTrainingVm pour {}", request.getVmName());

        Map<String, String> nodeToIp = Map.of(
                "ceph2", "192.168.13.22",
                "ceph3", "192.168.13.33",
                "ceph4", "192.168.13.44"
        );
        String vmName = request.getVmName();
        String username = request.getUsername();

        // 0️⃣ Persist DB
        VmInstance vm = new VmInstance();
        vm.setUsername(username);
        vm.setVmName(vmName);
        vm.setStorageType(request.getStorageType() != null ? request.getStorageType() : "RBD");
        vm.setStatus("CREATED");
        vm.setCreatedAt(LocalDateTime.now());
        vm.setSize(request.getSize());
        vm.setOsType(request.getOsType());
        vmInstanceRepository.save(vm);
        log.debug("VM enregistrée en base : {}", vm);

        try {
            // 1️⃣ Générer le YAML
            log.info("Génération du manifeste YAML pour {}", vmName);
            String yamlContent = yamlGeneratorService.generateYaml(request);
            log.debug("YAML généré :\n{}", yamlContent);

            // 2️⃣ Appliquer via kubectl
            log.info("Application du manifeste YAML sur le cluster");
            String kubectlOutput = applyYamlToCluster(yamlContent);
            log.info("kubectl apply renvoyé :\n{}", kubectlOutput);

            // 3️⃣ Pause pour laisser le pod démarrer
            log.info("Attente de 10s pour démarrage du pod {}", vmName);
            Thread.sleep(10_000);

            // 4️⃣ Trouver le nœud hébergeant le pod
            log.info("Récupération du nœud pour le pod {}", vmName);
            String nodeName = getNodeHostingPod(vmName);
            log.info("Le pod {} est sur le nœud {}", vmName, nodeName);

            String ip = nodeToIp.get(nodeName);
            if (ip == null) {
                throw new RuntimeException("Aucune IP configurée pour le nœud " + nodeName);
            }

            // 5️⃣ Test SSH
            log.info("Exécution d'une commande de test en SSH sur {}@{}", username, ip);
            String sshOutput = executeCommand(ip, username, request.getPassword(),
                    "echo Hello depuis " + vmName);
            log.info("SSH test renvoyé :\n{}", sshOutput);

            // 6️⃣ Succès
            return String.format(
                    "✅ VM '%s' déployée avec succès !\n\nkubectl:\n%s\n\nSSH test:\n%s",
                    vmName, kubectlOutput, sshOutput
            );

        } catch (Exception e) {
            log.error("Erreur lors de la création de la VM {}", vmName, e);
            return "❌ Erreur lors du déploiement de " + vmName + " : " + e.getMessage();
        }
    }

    /**
     * Récupère le nom du nœud où tourne le pod (namespace 'vm').
     */
    private String getNodeHostingPod(String podName) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "kubectl", "get", "pod", podName, "-n", "vm",
                "-o", "jsonpath={.spec.nodeName}"
        );
        pb.redirectErrorStream(true);
        log.debug("Lancement de la commande : {}", String.join(" ", pb.command()));

        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String node = reader.readLine();
            int code = proc.waitFor();
            if (code != 0) {
                throw new RuntimeException("kubectl get pod exit code " + code);
            }
            return node;
        }
    }

    /**
     * Exécute une commande SSH sur une machine distante.
     */
    public String executeCommand(String ip, String username, String password, String command) {
        log.debug("executeCommand SSH -> {}@{} : {}", username, ip, command);
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(username, ip, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10_000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);
            InputStream in = channel.getInputStream();
            channel.connect();

            StringBuilder out = new StringBuilder();
            byte[] buf = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int len = in.read(buf, 0, buf.length);
                    if (len < 0) break;
                    out.append(new String(buf, 0, len));
                }
                if (channel.isClosed()) break;
                Thread.sleep(200);
            }
            log.debug("SSH exit status: {}", channel.getExitStatus());
            return out.toString();

        } catch (JSchException|IOException|InterruptedException e) {
            log.error("Erreur SSH sur {}@{}", username, ip, e);
            throw new RuntimeException("SSH failed: " + e.getMessage(), e);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Sauvegarde le YAML dans un fichier temporaire et l'applique
     * via 'kubectl apply -f'.
     */
    public String applyYamlToCluster(String yamlContent) throws IOException, InterruptedException {
        // 1️⃣ Écrire dans un fichier temp
        File tmp = File.createTempFile("vm-", ".yaml");
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write(yamlContent);
        }
        log.debug("Manifeste écrit dans {}", tmp.getAbsolutePath());

        // 2️⃣ Lancer kubectl
        ProcessBuilder pb = new ProcessBuilder("kubectl", "apply", "-f", tmp.getAbsolutePath());
        pb.redirectErrorStream(true);
        log.debug("Lancement de la commande : {}", String.join(" ", pb.command()));

        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append(System.lineSeparator());
            }
        }
        int code = proc.waitFor();
        tmp.delete();

        log.debug("kubectl apply exit code: {}", code);
        if (code != 0) {
            log.error("kubectl apply failed: \n{}", out);
            throw new RuntimeException("Erreur kubectl apply (code " + code + "):\n" + out);
        }
        return out.toString();
    }
}
