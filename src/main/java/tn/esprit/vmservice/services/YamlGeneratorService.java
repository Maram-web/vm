package tn.esprit.vmservice.services;

import org.springframework.stereotype.Service;
import tn.esprit.vmservice.dto.VmRequest;

@Service
public class YamlGeneratorService {

    public String generateYaml(VmRequest req) {
        return """
apiVersion: v1
kind: Pod
metadata:
  name: %s
spec:
  containers:
    - name: %s-container
      image: %s
      resources:
        limits:
          cpu: %s
          memory: %s
      volumeMounts:
        - mountPath: /data
          name: storage
  volumes:
    - name: storage
      emptyDir: {}
""".formatted(
                req.getVmName(),
                req.getVmName(),
                getImage(req.getOsType(), req.getUbuntuImageTag()),
                getCpu(req.getSize()),
                getMemory(req.getSize())
        );
    }

    private String getImage(String os, String ubuntuTag) {
        return os.equalsIgnoreCase("ubuntu")
                ? "marammanai/ubuntu-ssh-kubectl:" + ubuntuTag
                : "mcr.microsoft.com/windows/nanoserver";
    }



    private String getCpu(String size) {
        return switch (size.toLowerCase()) {
            case "small" -> "500m";
            case "medium" -> "1";
            case "large" -> "2";
            default -> "500m";
        };
    }

    private String getMemory(String size) {
        return switch (size.toLowerCase()) {
            case "small" -> "512Mi";
            case "medium" -> "1Gi";
            case "large" -> "2Gi";
            default -> "512Mi";
        };
    }
}
