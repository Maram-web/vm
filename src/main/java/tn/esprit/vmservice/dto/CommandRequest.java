package tn.esprit.vmservice.dto;

import lombok.Data;

@Data
public class CommandRequest {
    private String ip;
    private String username;
    private String password;
    private String command;
}
