package tn.esprit.vmservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.vmservice.dto.VmRequest;
import tn.esprit.vmservice.services.VmService;

@RestController
@RequestMapping("/api/vm")
@RequiredArgsConstructor
public class VmController {

    private final VmService vmService;

    @PostMapping("/create")
    public ResponseEntity<String> createVm(@RequestBody VmRequest request) {
        String result = vmService.createTrainingVm(request.getUsername());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello from vm-service! ✅";
    }
}
