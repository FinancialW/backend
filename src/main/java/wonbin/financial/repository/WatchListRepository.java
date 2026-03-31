package wonbin.financial.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import wonbin.financial.entity.WatchList;

@Repository
public interface WatchListRepository extends JpaRepository<WatchList,Long> {
    List<WatchList> findByUserId(String userId);
}
