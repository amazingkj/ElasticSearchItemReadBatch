package io.spring.cloud.elastic.job;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.data.AbstractPaginatedDataItemReader;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.CriteriaQueryBuilder;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static org.springframework.util.Assert.state;

@Slf4j
public class ElasticsearchItemReader<T> extends AbstractPaginatedDataItemReader<T> implements InitializingBean {
    public static final String DEFAULT_SORT_OPTION = "_id";
    private final ElasticsearchOperations operations;
    private final Class<T> targetType;
    private final Criteria criteria;
    private List<Object> searchAfter;
    private Sort sortOptions;


    @Builder
    public ElasticsearchItemReader(ElasticsearchOperations operations, Class<T> targetType, Criteria criteria,
                                   String name, int pageSize, Sort sortOptions) {
        this.operations = operations;
        this.targetType = targetType;
        this.criteria = criteria;
        this.searchAfter = new ArrayList<>();
        initializeSort(sortOptions);
        super.setName(name);
        super.setPageSize(pageSize);
    }

    private void initializeSort(Sort sortOptions) {
        Sort defaultSort = Sort.by(DEFAULT_SORT_OPTION);
        if (Objects.isNull(sortOptions) || sortOptions.isEmpty()) {
            this.sortOptions = defaultSort;
            log.warn("Sort Option is Empty. Applying Default Sort Option : {}", DEFAULT_SORT_OPTION);
            return;
        }

        if (Objects.isNull(sortOptions.getOrderFor(DEFAULT_SORT_OPTION))) {
            this.sortOptions = sortOptions.and(defaultSort);
            return;
        }
        this.sortOptions = sortOptions;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        state(operations != null, "An ElasticsearchOperations implementation is required.");
        state(targetType != null, "A target type to convert the input into is required.");
        state(criteria != null, "A criteria is required.");
    }

    @NonNull
    @Override
    protected Iterator<T> doPageRead() {
        PageRequest pageable = PageRequest.of(0, pageSize, sortOptions);
        CriteriaQuery query = new CriteriaQueryBuilder(criteria)
                .withPageable(pageable)
                .build();
        if (!searchAfter.isEmpty()) {
            query.setSearchAfter(searchAfter);
        }
        List<SearchHit<T>> searchHits = operations.search(query, targetType).toList();

        searchAfter.clear();
        if (!searchHits.isEmpty()) {
            searchAfter.addAll(searchHits.get(searchHits.size() - 1).getSortValues());
        }

        return searchHits.stream().map(SearchHit::getContent).iterator();
    }
}
