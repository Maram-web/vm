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
import java.util.Map;

@RestController
@RequestMapping("/api/vm")
@RequiredArgsConstructor
public class VmController {
    private final K8sClusterService k8sClusterService;

    private final VmService vmService;
    private final VmInstanceRepository vmInstanceRepository;

    @PostMapping("/{name}/exec")
    public ResponseEntity<String> execCommand(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {

        try {
            String command = body.get("command");
            // Pour la démo on fixe l’IP, l’utilisateur, etc.
            String ip = "192.168.122.45";  // à remplacer dynamiquement plus tard
            String username = "springuser";
            String password = "springpass";

            String result = vmService.executeCommand(ip, username, password, command);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Erreur d’exécution : " + e.getMessage());
        }
    }
    @PostMapping("/create")
    public ResponseEntity<String> createVm(@RequestBody VmRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        request.setUsername(username); // injecte le vrai username depuis le token

        String vmName = request.getVmName();

        // 🔍 Vérification si une VM avec le même nom existe déjà pour cet utilisateur
        boolean exists = vmInstanceRepository.existsByUsernameAndVmName(username, vmName);
        if (exists) {
            return ResponseEntity.status(400).body("❌ Une VM nommée '" + vmName + "' existe déjà pour l'utilisateur " + username);
        }

        // ✅ Création de la VM
        VmInstance vm = new VmInstance();
        vm.setUsername(username);
        vm.setVmName(vmName);
        vm.setStorageType(request.getStorageType());
        vm.setStatus("Créée");
        vm.setCreatedAt(java.time.LocalDateTime.now());

        vmInstanceRepository.save(vm);

        System.out.println("✅ Création VM pour : " + username);

        // (Tu peux ici ajouter l'appel à kubectl ou script de création)
        vm.setDisplayName(request.getVmName());  // ou request.getDisplayName() si tu changes le DTO


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
