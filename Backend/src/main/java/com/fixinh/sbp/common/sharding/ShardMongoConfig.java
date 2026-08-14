package com.fixinh.sbp.common.sharding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import com.fixinh.sbp.common.config.SbpProperties;

/**
 * The two regional Mongo stores (Req 3.1.U1, 5.S1).
 *
 * <p>Spring Data's derived-query {@code MongoRepository} binds to exactly one template, so the
 * customer store is hand-written against both of these instead — see
 * {@code ShardedCustomerRepository}.
 *
 * <p>South is {@code @Primary} purely so that anything injecting a bare {@code MongoTemplate}
 * resolves rather than failing on ambiguity; shard-aware code always qualifies.
 */
@Configuration
public class ShardMongoConfig {

	public static final String SOUTH_TEMPLATE = "southMongoTemplate";
	public static final String NORTH_TEMPLATE = "northMongoTemplate";

	@Bean(SOUTH_TEMPLATE)
	@Primary
	public MongoTemplate southMongoTemplate(SbpProperties properties, MongoMappingContext mappingContext,
			MongoCustomConversions conversions) {
		return buildTemplate(properties.getMongo().getSouth().getUri(), mappingContext, conversions);
	}

	@Bean(NORTH_TEMPLATE)
	public MongoTemplate northMongoTemplate(SbpProperties properties, MongoMappingContext mappingContext,
			MongoCustomConversions conversions) {
		return buildTemplate(properties.getMongo().getNorth().getUri(), mappingContext, conversions);
	}

	/**
	 * Both templates share the auto-configured mapping context, so entity metadata — including
	 * {@code spring.data.mongodb.auto-index-creation} — applies identically to each shard.
	 */
	private MongoTemplate buildTemplate(String uri, MongoMappingContext mappingContext,
			MongoCustomConversions conversions) {

		MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(uri);

		MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory),
				mappingContext);
		converter.setCustomConversions(conversions);
		// Drop the "_class" discriminator: these documents are read back as one known type.
		converter.setTypeMapper(new DefaultMongoTypeMapper(null));
		converter.afterPropertiesSet();

		return new MongoTemplate(factory, converter);
	}
}
