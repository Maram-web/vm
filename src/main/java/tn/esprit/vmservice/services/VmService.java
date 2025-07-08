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




}
