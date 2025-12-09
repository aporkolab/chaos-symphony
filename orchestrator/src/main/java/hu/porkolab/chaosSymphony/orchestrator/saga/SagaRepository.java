package hu.porkolab.chaosSymphony.orchestrator.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;


@Repository
public interface SagaRepository extends JpaRepository<SagaInstance, String> {

    
    List<SagaInstance> findByState(SagaState state);

    
    @Query("SELECT s FROM SagaInstance s WHERE s.state IN :states AND s.updatedAt < :cutoff")
    List<SagaInstance> findStuckSagas(List<SagaState> states, Instant cutoff);

    
    long countByState(SagaState state);
}
