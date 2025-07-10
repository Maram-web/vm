package tn.esprit.vmservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.vmservice.dto.ClusterRequest;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.repositories.VmInstanceRepository;
import tn.esprit.vmservice.services.K8sClusterService;
import tn.esprit.vmservice.services.VmService;

import java.util.List;

@RestController
@RequestMapping("/api/vm")
@RequiredArgsConstructor
public class VmController {
    private final K8sClusterService k8sClusterService;

    private final VmService vmService;
    private final VmInstanceRepository vmInstanceRepository;

    @PostMapping("/create")
    public ResponseEntity<String> createVm(@RequestBody VmRequest request) {
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

    @GetMapping("/by-user/{username}")
    public List<VmInstance> getVMsByUser(@PathVariable String username) {
        return vmInstanceRepository.findByUsername(username);
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
    @DeleteMapping("/delete/{vmName}")
    public ResponseEntity<String> deleteVm(@PathVariable String vmName) {
        // appel kubectl delete
        return ResponseEntity.ok("VM supprimée : " + vmName);
    }

}
