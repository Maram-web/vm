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

import java.util.List;
import java.util.Map;

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
            String output = vmService.executeCommand(
                    request.getIp(),
                    request.getUsername(),
                    request.getPassword(),
                    request.getCommand()
            );
            return ResponseEntity.ok(output);
        } catch (Exception e) {
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

        VmInstance vm = new VmInstance();
        vm.setUsername(username);
        vm.setVmName(vmName);
        vm.setStorageType(request.getStorageType());
        vm.setStatus("Créée");
        vm.setCreatedAt(java.time.LocalDateTime.now());
        vm.setDisplayName(vmName);

        vmInstanceRepository.save(vm);
        return ResponseEntity.ok("✅ VM créée avec succès : " + vmName);
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

    @GetMapping("/details/{vmName}")
    public ResponseEntity<VmInstance> getVmDetails(@PathVariable String vmName) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        VmInstance vm = vmInstanceRepository.findByVmName(vmName);
        if (vm == null) return ResponseEntity.notFound().build();
        if (!vm.getUsername().equals(username)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(vm);
    }

    @DeleteMapping("/delete/{vmName}")
    public ResponseEntity<String> deleteVm(@PathVariable String vmName) {
        // TODO: add delete logic
        return ResponseEntity.ok("VM supprimée : " + vmName);
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
