package io.newsworld.collector.repository;

import io.newsworld.collector.model.FeedCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedCacheRepository extends JpaRepository<FeedCache, String> {
}
