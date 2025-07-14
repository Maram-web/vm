package tn.esprit.vmservice.services;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.LocalDateTime;
import java.util.Map;
/**
 * Service de gestion des VMs.
 */
@Service
@RequiredArgsConstructor
public class VmService {
    private static final Logger log = LoggerFactory.getLogger(VmService.class);

    private final VmInstanceRepository vmInstanceRepository;
    private final YamlGeneratorService yamlGeneratorService;
    @Value("${ssh.password}")
    private String sshPassword; // Injecte automatiquement "maram"




    public String createTrainingVm(VmRequest request) {
        log.info("Début déploiement VM {}", request.getVmName());

        Map<String, String> nodeToIp = Map.of(
                "ceph1-virtual-machine", "192.168.13.11",
                "ceph2-virtual-machine", "192.168.13.22",
                "ceph3-virtual-machine", "192.168.13.33",
                "ceph4-virtual-machine", "192.168.13.44"
        );

        Map<String, String> nodeToUser = Map.of(
                "ceph1-virtual-machine", "ceph1",
                "ceph2-virtual-machine", "ceph2",
                "ceph3-virtual-machine", "ceph3",
                "ceph4-virtual-machine", "ceph4"
        );

        // 0) Persistance
        VmInstance vm = new VmInstance();
        vm.setUsername(request.getUsername());
        vm.setVmName(request.getVmName());
        vm.setStorageType(request.getStorageType() != null ? request.getStorageType() : "RBD");
        vm.setOsType(request.getOsType());
        vm.setSize(request.getSize());
        vm.setStatus("CREATED");
        vm.setCreatedAt(LocalDateTime.now());
        vmInstanceRepository.save(vm);
        log.debug("VM enregistrée en base : {}", vm);

        try {
            // 1) Génération YAML
            log.info("Génération du YAML pour {}", request.getVmName());
            String yaml = yamlGeneratorService.generateYaml(request);
            log.debug("YAML généré :\n{}", yaml);

            // 2) kubectl apply
            log.info("Application du manifeste sur le cluster");
            String kubectlOut = applyYamlToCluster(yaml);
            log.info("kubectl apply retourné :\n{}", kubectlOut);

            // 3) Pause démarrage
            log.info("Attente du démarrage du pod {}", request.getVmName());
            Thread.sleep(10_000);

            // 4) Découverte nœud
            log.info("Récupération du nœud hébergeant {}", request.getVmName());
            String node = getNodeHostingPod(request.getVmName());
            log.info("Le pod {} est sur le nœud {}", request.getVmName(), node);

            String ip = nodeToIp.get(node);
            String user = nodeToUser.get(node);

            if (ip == null || user == null) {
                throw new RuntimeException("No mapping found for node: " + node);
            }

            // 5) Test SSH
            log.info("Test SSH vers {}@{}", user, ip);
            String sshOut = executeCommand(ip, user, "maram", "echo Hello depuis " + request.getVmName());
            log.info("SSH test renvoyé :\n{}", sshOut);

            // 6) Retour final
            return "✅ Déploiement réussi.\n\n— kubectl —\n" + kubectlOut +
                    "\n\n— SSH test —\n" + sshOut;

        } catch (Exception e) {
            log.error("Erreur déploiement VM {}", request.getVmName(), e);
            return "❌ Échec déploiement : " + e.getMessage();
        }
    }

    /**
     * Renvoie le nom du nœud Kubernetes qui héberge le pod.
     */
    public String getNodeHostingPod(String podName) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "kubectl", "get", "pod", podName, "-n", "vm",
                "-o", "jsonpath={.spec.nodeName}"
        );
        pb.redirectErrorStream(true);
        log.debug("Lancement : {}", String.join(" ", pb.command()));
        Process p = pb.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String node = br.readLine();
            int code = p.waitFor();
            if (code != 0) {
                throw new RuntimeException("kubectl get pod exit code: " + code);
            }
            return node;
        }
    }

    /**
     * Execute une commande SSH et renvoie le résultat.
     */
    public String executeCommand(String ip,
                                 String user,
                                 String password,
                                 String command) {
        log.debug("SSH exec: {}@{} => {}", user, ip, command);
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(user, ip, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(10_000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
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
            log.debug("SSH exit-status: {}", channel.getExitStatus());
            return out.toString();

        } catch (Exception e) {
            log.error("SSH error {}@{}", user, ip, e);
            throw new RuntimeException("SSH failed: " + e.getMessage(), e);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Enregistre le YAML dans un fichier temporaire et fait kubectl apply.
     */
    public String applyYamlToCluster(String yamlContent) throws IOException, InterruptedException {
        File tmp = File.createTempFile("vm-", ".yaml");
        try (FileWriter fw = new FileWriter(tmp)) {
            fw.write(yamlContent);
        }
        log.debug("YAML temporaire écrit : {}", tmp.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder("kubectl", "apply", "-f", tmp.getAbsolutePath());
        pb.redirectErrorStream(true);
        log.debug("Lancement : {}", String.join(" ", pb.command()));
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        int code = p.waitFor();
        tmp.delete();
        log.debug("kubectl apply exit code: {}", code);

        if (code != 0) {
            log.error("kubectl apply a échoué :\n{}", out);
            throw new RuntimeException("Erreur kubectl apply (code " + code + ")\n" + out);
        }
        return out.toString();
    }
}
