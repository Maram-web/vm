package tn.esprit.vmservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tn.esprit.vmservice.dto.ClusterRequest;
import tn.esprit.vmservice.dto.CommandRequest;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.repositories.VmInstanceRepository;
import tn.esprit.vmservice.services.K8sClusterService;
import tn.esprit.vmservice.services.VmService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/vm")
@RequiredArgsConstructor
public class VmController {

    private final K8sClusterService k8sClusterService;
    private final VmService vmService;
    private final VmInstanceRepository vmInstanceRepository;

    // ────────────────────────────────────────────────────────────────────────────
    // Exécution de commande SSH (via body JSON)
    // ────────────────────────────────────────────────────────────────────────────
    @PostMapping("/execute")
    public ResponseEntity<String> executeCommand(@RequestBody CommandRequest request) {
        log.info("🎯 /execute appelée : IP={}, user={}, cmd={}, pass={}",
                request.getIp(), request.getUsername(), request.getCommand(), request.getPassword());

        try {
            String output = vmService.executeCommand(
                    request.getIp(),
                    request.getUsername(),
                    request.getPassword(),
                    request.getCommand()
            );
            log.info("✅ SSH exec réussi, sortie:\n{}", output);
            return ResponseEntity.ok(output);
        } catch (Exception e) {
            log.error("❌ Erreur SSH exec : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ Erreur SSH : " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Création de VM
    // ────────────────────────────────────────────────────────────────────────────
    @PostMapping("/create")
    public ResponseEntity<String> createVm(@RequestBody VmRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        request.setUsername(username);
        log.info("🎯 /create appelée par '{}' pour VM='{}'", username, request.getVmName());

        // Vérifier unicité
        boolean exists = vmInstanceRepository.existsByUsernameAndVmName(username, request.getVmName());
        if (exists) {
            log.warn("⚠️ La VM '{}' existe déjà pour l'utilisateur {}", request.getVmName(), username);
            return ResponseEntity
                    .badRequest()
                    .body("❌ Une VM nommée '" + request.getVmName() + "' existe déjà.");
        }

        // Délégation au service
        String result = vmService.createTrainingVm(request);
        log.info("✅ Résultat createTrainingVm:\n{}", result);
        return ResponseEntity.ok(result);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Endpoint de test
    // ────────────────────────────────────────────────────────────────────────────
    @GetMapping("/hello")
    public ResponseEntity<String> sayHello() {
        log.info("🎯 /hello appelée");
        return ResponseEntity.ok("Hello from vm-service! ✅");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Lister toutes les VMs (toutes entrées DB)
    // ────────────────────────────────────────────────────────────────────────────
    @GetMapping("/all")
    public ResponseEntity<List<VmInstance>> getAllVMs() {
        log.info("🎯 /all appelée");
        List<VmInstance> vms = vmInstanceRepository.findAll();
        log.info("✅ {} VM(s) récupérées", vms.size());
        return ResponseEntity.ok(vms);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Lister VMs de l'utilisateur courant
    // ────────────────────────────────────────────────────────────────────────────
    @GetMapping("/my-vms")
    public ResponseEntity<List<VmInstance>> getMyVMs() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        log.info("🎯 /my-vms appelée par '{}'", username);
        List<VmInstance> vms = vmInstanceRepository.findByUsername(username);
        log.info("✅ {} VM(s) pour l'utilisateur '{}'", vms.size(), username);
        return ResponseEntity.ok(vms);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Détails d'une VM (avec IP du nœud)
    // ────────────────────────────────────────────────────────────────────────────
    @GetMapping("/details/{name}")
    public ResponseEntity<?> getVmDetails(@PathVariable String name) {
        log.info("🎯 /details/{} appelée", name);
        VmInstance vm = vmInstanceRepository.findByVmName(name);
        if (vm == null) {
            log.warn("⚠️ VM '{}' introuvable", name);
            return ResponseEntity.notFound().build();
        }

        // Mapping des nœuds → IP
        // Mapping des nœuds → IP
        Map<String, String> nodeToIp = Map.of(
                "ceph1-virtual-machine", "192.168.13.11",
                "ceph2-virtual-machine", "192.168.13.22",
                "ceph3-virtual-machine", "192.168.13.33",
                "ceph4-virtual-machine", "192.168.13.44"
        );

        try {
            String nodeName = vmService.getNodeHostingPod(name);
            log.info("📍 Le pod '{}' est sur le nœud '{}'", name, nodeName);

            String ip = nodeToIp.get(nodeName);
            if (ip == null) {
                log.error("❌ Aucun mapping IP trouvé pour le nœud '{}'", nodeName);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("❌ IP inconnue pour le nœud : " + nodeName);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("vmName",    vm.getVmName());
            response.put("size",      vm.getSize());
            response.put("osType",    vm.getOsType());
            response.put("createdAt", vm.getCreatedAt());
            response.put("status",    vm.getStatus());
            response.put("ip",        ip);

            log.info("✅ Détails pour '{}': {}", name, response);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erreur getVmDetails pour '{}'", name, e);
            return ResponseEntity.status(500)
                    .body("❌ Erreur récupération IP : " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Suppression d'une VM
    // ────────────────────────────────────────────────────────────────────────────
    @DeleteMapping("/delete/{vmName}")
    public ResponseEntity<String> deleteVm(@PathVariable String vmName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        log.info("🎯 /delete/{} appelée par '{}'", vmName, username);

        VmInstance vm = vmInstanceRepository.findByVmName(vmName);
        if (vm == null || !vm.getUsername().equals(username)) {
            log.warn("⚠️ VM '{}' introuvable ou non autorisée pour '{}'", vmName, username);
            return ResponseEntity.status(404)
                    .body("❌ VM introuvable ou non autorisée.");
        }

        try {
            // 1) Supprimer le pod Kubernetes
            log.info("Suppression du pod Kubernetes '{}'", vmName);
            ProcessBuilder pb = new ProcessBuilder("kubectl", "delete", "pod", vmName, "-n", "vm");
            pb.redirectErrorStream(true);
            log.debug("Lancement : {}", String.join(" ", pb.command()));
            Process p = pb.start();

            StringBuilder podOut = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    podOut.append(line).append('\n');
                }
            }
            int code = p.waitFor();
            log.debug("kubectl delete exit code: {}", code);
            if (code != 0) {
                log.error("Erreur suppression pod :\n{}", podOut);
                return ResponseEntity.status(500)
                        .body("❌ Erreur suppression Kubernetes :\n" + podOut);
            }

            // 2) Supprimer la trace en base
            vmInstanceRepository.delete(vm);
            log.info("✅ VM '{}' supprimée (pod + DB)", vmName);
            return ResponseEntity.ok("✅ VM supprimée avec succès : " + vmName);

        } catch (Exception e) {
            log.error("❌ Erreur deleteVm pour '{}'", vmName, e);
            return ResponseEntity.status(500)
                    .body("❌ Erreur suppression : " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Création de cluster
    // ────────────────────────────────────────────────────────────────────────────
    @PostMapping("/create-cluster")
    public ResponseEntity<String> createCluster(@RequestBody ClusterRequest request) {
        log.info("🎯 /create-cluster appelée : {}", request);
        try {
            String response = k8sClusterService.createK8sCluster(
                    request.getUsername(),
                    request.getClusterName(),
                    request.getWorkerCount(),
                    request.getOsType(),
                    request.getSize()
            );
            log.info("✅ createK8sCluster renvoyé : {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Erreur createCluster : {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body("❌ Erreur interne : " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test SSH via endpoint (utilisé pour debug)
    // ────────────────────────────────────────────────────────────────────────────
    @GetMapping("/test-ssh")
    public ResponseEntity<String> testSshCommand() {
        log.info("🎯 /test-ssh appelée");
        try {
            String result = vmService.executeCommand(
                    "192.168.122.101",
                    "springuser",
                    "tonPassword",
                    "ls -l"
            );
            log.info("✅ resultat test-ssh :\n{}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Erreur test-ssh : {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body("❌ Erreur : " + e.getMessage());
        }
    }

}
