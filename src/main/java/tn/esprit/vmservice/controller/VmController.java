package tn.esprit.vmservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.vmservice.dto.ClusterRequest;
import tn.esprit.vmservice.dto.CommandRequest;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.repositories.VmInstanceRepository;
import tn.esprit.vmservice.services.K8sClusterService;
import tn.esprit.vmservice.services.VmService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;

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

    // 🔧 SSH Command Execution - via body
    @PostMapping("/execute")
    public ResponseEntity<String> executeCommand(@RequestBody CommandRequest request) {
        try {
            log.info("🎯 Tentative d'exécution de commande SSH :");
            log.info("   ➤ IP: {}", request.getIp());
            log.info("   ➤ Utilisateur: {}", request.getUsername());
            log.info("   ➤ Commande: {}", request.getCommand());

            String output = vmService.executeCommand(
                    request.getIp(),
                    request.getUsername(),
                    request.getPassword(),
                    request.getCommand()
            );

            log.info("✅ Résultat de la commande : \n{}", output);

            return ResponseEntity.ok(output);
        } catch (Exception e) {
            log.error("❌ Erreur pendant l'exécution SSH : {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("❌ Erreur : " + e.getMessage());
        }
    }


    // ✅ Create VM
    @PostMapping("/create")
    public ResponseEntity<String> createVm(@RequestBody VmRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        request.setUsername(username);

        String vmName = request.getVmName();
        boolean exists = vmInstanceRepository.existsByUsernameAndVmName(username, vmName);
        if (exists) {
            return ResponseEntity.badRequest().body("❌ Une VM nommée '" + vmName + "' existe déjà.");
        }

        // ✅ Délégation au service qui fait tout : DB + YAML + kubectl
        String result = vmService.createTrainingVm(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello from vm-service! ✅";
    }

    @GetMapping("/all")
    public List<VmInstance> getAllVMs() {
        return vmInstanceRepository.findAll();
    }

    @GetMapping("/my-vms")
    public List<VmInstance> getMyVMs() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return vmInstanceRepository.findByUsername(username);
    }

    @GetMapping("/details/{name}")
    public ResponseEntity<?> getVmDetails(@PathVariable String name) {
        VmInstance vm = vmInstanceRepository.findByVmName(name);
        if (vm == null) return ResponseEntity.notFound().build();

        Map<String, String> nodeToIp = Map.of(
                "ceph2", "192.168.13.22",
                "ceph3", "192.168.13.33",
                "ceph4", "192.168.13.44"
        );

        try {
            String nodeName = vmService.getNodeHostingPod(vm.getVmName());
            String ip = nodeToIp.get(nodeName);

            Map<String, Object> response = new HashMap<>();
            response.put("vmName", vm.getVmName());
            response.put("size", vm.getSize());
            response.put("osType", vm.getOsType());
            response.put("createdAt", vm.getCreatedAt());
            response.put("status", vm.getStatus());
            response.put("ip", ip); // 👈 essentiel pour le terminal

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur récupération IP : " + e.getMessage());
        }
    }


    @DeleteMapping("/delete/{vmName}")
    public ResponseEntity<String> deleteVm(@PathVariable String vmName) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        VmInstance vm = vmInstanceRepository.findByVmName(vmName);
        if (vm == null || !vm.getUsername().equals(username)) {
            return ResponseEntity.status(404).body("❌ VM introuvable ou non autorisée.");
        }

        try {
            // Supprimer le pod dans K8s
            String cmd = "kubectl delete pod " + vmName + " -n vm";
            Process process = Runtime.getRuntime().exec(cmd);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return ResponseEntity.status(500).body("❌ Erreur lors de la suppression dans Kubernetes.");
            }

            // Supprimer de la BDD
            vmInstanceRepository.delete(vm);

            return ResponseEntity.ok("✅ VM supprimée avec succès : " + vmName);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Erreur lors de la suppression : " + e.getMessage());
        }
    }


    @PostMapping("/create-cluster")
    public ResponseEntity<String> createCluster(@RequestBody ClusterRequest request) {
        try {
            String response = k8sClusterService.createK8sCluster(
                    request.getUsername(),
                    request.getClusterName(),
                    request.getWorkerCount(),
                    request.getOsType(),
                    request.getSize()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur interne : " + e.getMessage());
        }
    }

    @GetMapping("/test-ssh")
    public ResponseEntity<String> testSshCommand() {
        try {
            String result = vmService.executeCommand("192.168.122.101", "springuser", "tonPassword", "ls -l");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur : " + e.getMessage());
        }
    }
}
