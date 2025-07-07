package tn.esprit.vmservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.vmservice.entity.VmInstance;

import java.util.List;

@Repository
public interface VmInstanceRepository extends JpaRepository<VmInstance, Long> {
    List<VmInstance> findByUsername(String username);
}
