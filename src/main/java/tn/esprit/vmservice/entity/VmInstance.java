package tn.esprit.vmservice.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "vm_instances")
public class VmInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;         // Nom de l'utilisateur
    private String vmName;           // Nom donné à la VM
    private String storageType;      // RBD ou CephFS
    private String status;           // Créée / En cours / Supprimée
    private LocalDateTime createdAt; // Date de création
    private String displayName; // Nom saisi par l'utilisateur lors de la création

}
