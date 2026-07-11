package wonbin.financial.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import wonbin.financial.entity.WatchList;

@Repository
public interface WatchListRepository extends JpaRepository<WatchList,Long> {
    List<WatchList> findByUserId(String userId);
    boolean existsByUserIdAndSymbol(String userId, String symbol);

    Optional<WatchList> findByUserIdAndSymbol(String userId, String symbol);

    List<WatchList> findBySymbol(String symbol);

    @Query("select distinct w.symbol from WatchList w")
    List<String> findDistinctSymbols();
}
