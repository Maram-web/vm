package tn.esprit.vmservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VmRequest {
    private String username;
    private String vmName;       // Optionnel : sinon généré
    private String osType;       // ex: ubuntu / windows
    private String size;         // ex: small / medium / large
    private String storageType;  // RBD / CephFS / S3
}
