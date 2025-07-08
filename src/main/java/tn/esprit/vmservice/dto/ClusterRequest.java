
package tn.esprit.vmservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClusterRequest {
    private String username;
    private String clusterName;
    private int workerCount;
    private String osType;    // ex: "ubuntu"
    private String size;      // ex: "small"
}
