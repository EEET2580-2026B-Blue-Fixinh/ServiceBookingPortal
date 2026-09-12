package com.fixinh.sbp.common.sharding;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

import com.fixinh.sbp.modules.admin.model.Administrator;

/**
 * The administrators' email index, with both templates mocked.
 *
 * <p>Every other collection is handed an index set with nothing in it, so the rest of
 * {@link ShardIndexInitializer#ensureIndexes()} runs and falls into its own logged catch
 * blocks; only the administrator collection, which lives in the south store alone, is under
 * test here.
 */
class ShardIndexInitializerTest {

	private final MongoTemplate south = mock(MongoTemplate.class);
	private final MongoTemplate north = mock(MongoTemplate.class);
	private final IndexOperations administratorIndexes = mock(IndexOperations.class);

	@BeforeEach
	void setUp() {
		IndexOperations empty = mock(IndexOperations.class);
		when(empty.getIndexInfo()).thenReturn(List.of());
		when(south.indexOps(any(Class.class))).thenReturn(empty);
		when(north.indexOps(any(Class.class))).thenReturn(empty);
		when(south.indexOps(Administrator.class)).thenReturn(administratorIndexes);
	}

	@Test
	void leavesAnExistingEmailIndexAloneWhateverItIsCalled() {
		// Named "email", as @Indexed names it. Asking for "email_1" over the same field is what
		// Mongo refused with IndexOptionsConflict, printing a stack trace on every startup.
		when(administratorIndexes.getIndexInfo()).thenReturn(List.of(indexOn("email", "email")));

		new ShardIndexInitializer(south, north).ensureIndexes();

		verify(administratorIndexes, never()).createIndex(any());
	}

	@Test
	void createsTheEmailIndexWhenNoIndexCoversIt() {
		when(administratorIndexes.getIndexInfo()).thenReturn(List.of(indexOn("_id_", "_id")));

		new ShardIndexInitializer(south, north).ensureIndexes();

		verify(administratorIndexes).createIndex(any());
	}

	private static IndexInfo indexOn(String name, String field) {
		return new IndexInfo(List.of(IndexField.create(field, Sort.Direction.ASC)), name, false,
				false, "");
	}
}