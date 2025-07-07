package tn.esprit.vmservice.service;


import org.springframework.stereotype.Service;

@Service
public class VmService {

    public String createTrainingVm(String username) {
        String vmName = "vm-" + username;
        String rbdName = "rbd-" + username;

        try {
            Process process = new ProcessBuilder(
                    "bash", "-c",
                    "virt-install --name " + vmName +
                            " --memory 1024 --vcpus 1 " +
                            " --disk /dev/rbd/rbd/" + rbdName +
                            " --import --os-variant ubuntu20.04 --graphics none " +
                            " --noautoconsole"
            ).start();

            process.waitFor();
            return "VM created successfully for user " + username;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
