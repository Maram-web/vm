package tn.esprit.vmservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.repositories.VmInstanceRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VmService {

    private final VmInstanceRepository vmInstanceRepository;

    public String createTrainingVm(String username) {
        // Logique de création de VM (YAML / RBD etc.)

        // Sauvegarde de la trace
        VmInstance vm = new VmInstance();
        vm.setUsername(username);
        vm.setVmName("training-vm-" + username);
        vm.setStorageType("RBD");
        vm.setStatus("CREATED");
        vm.setCreatedAt(LocalDateTime.now());

        vmInstanceRepository.save(vm);
        return "VM created for user: " + username;
    }
}
