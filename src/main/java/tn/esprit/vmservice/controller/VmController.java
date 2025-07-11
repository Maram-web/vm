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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        request.setUsername(username); // injecte le vrai username depuis le token

        String result = vmService.createTrainingVm(request);
        System.out.println("✅ Création VM pour : " + username);

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
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
    @GetMapping("/by-user")
    public ResponseEntity<List<VmInstance>> getUserVms() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        System.out.println("🔐 Utilisateur connecté : " + username);
        List<VmInstance> vms = vmInstanceRepository.findByUsername(username);
        return ResponseEntity.ok(vms);
    }
    @GetMapping("/details/{vmName}")
    public ResponseEntity<VmInstance> getVmDetails(@PathVariable String vmName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        VmInstance vm = vmInstanceRepository.findByVmName(vmName);
        if (vm == null) {
            return ResponseEntity.notFound().build();
        }

        System.out.println("🔍 Accès demandé par " + username + " pour VM: " + vm.getVmName());

        if (!vm.getUsername().equals(username)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(vm);
    }



}
