package tn.esprit.vmservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient

@SpringBootApplication
public class VmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VmServiceApplication.class, args);
    }

}
