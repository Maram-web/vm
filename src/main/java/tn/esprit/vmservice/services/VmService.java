package tn.esprit.vmservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.repositories.VmInstanceRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VmService {

    private final VmInstanceRepository vmInstanceRepository;
    private final YamlGeneratorService yamlGeneratorService;

    public String createTrainingVm(VmRequest request) {
        // 1. Génération dynamique du nom de VM
        String vmName = "training-vm-" + request.getUsername();

        // 2. Création d'une instance persistée
        VmInstance vm = new VmInstance();
        vm.setUsername(request.getUsername());
        vm.setVmName(vmName);
        vm.setStorageType(request.getStorageType() != null ? request.getStorageType() : "RBD");
        vm.setStatus("CREATED");
        vm.setCreatedAt(LocalDateTime.now());
        vmInstanceRepository.save(vm);

        // 3. Génération du YAML personnalisé
        String yamlContent = yamlGeneratorService.generateYaml(request);

        // (Optionnel) Affichage en console ou sauvegarde
        System.out.println("---- YAML GENERATED ----\n" + yamlContent);

        // TODO: Exécution automatique ou sauvegarde du YAML via `kubectl apply` si nécessaire

        return "VM created for user: " + request.getUsername();
    }
}
