package test.luvin_backend_v2.analysis.repository;

import com.luvin_backend_v2.analysis.domain.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {
    Optional<AnalysisResult> findTopByUserIdOrderByIdDesc(Long userId);
}