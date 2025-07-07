package tn.esprit.vmservice.controller;

import com.sun.jdi.VirtualMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.entity.VmInstance;
import tn.esprit.vmservice.services.VmService;
import tn.esprit.vmservice.repositories.VmInstanceRepository;

import java.util.List;


@RestController
@RequestMapping("/api/vm")
@RequiredArgsConstructor
public class VmController {

    private final VmService vmService;
    private final VmInstanceRepository vmInstanceRepository;


    @PostMapping("/create")
    public ResponseEntity<String> createVm(@RequestBody VmRequest request) {
        String result = vmService.createTrainingVm(request.getUsername());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello from vm-service! ✅";
    }

    @GetMapping("/all")
    public List<VmInstance> getAllVMs() {
        return vmInstanceRepository.findAll();
    }

    @GetMapping("/by-user/{username}")
    public List<VmInstance> getVMsByUser(@PathVariable String username) {
        return vmInstanceRepository.findByUsername(username);
    }

}
