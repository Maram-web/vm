package tn.esprit.vmservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VmRequest {
    private String username;
    private String vmName;
    private String osType;      // ubuntu / windows
    private String size;        // small / medium / large
    private String storageType; // RBD / S3 / CephFS (plus tard)
}
