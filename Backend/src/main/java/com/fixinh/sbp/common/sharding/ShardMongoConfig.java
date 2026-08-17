package com.fixinh.sbp.common.sharding;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
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
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * The two regional Mongo stores (Req 3.1.U1, 5.S1).
 *
 * <p>Spring Data's derived-query {@code MongoRepository} binds to exactly one template, so the
 * customer store is hand-written against both of these instead — see
 * {@code ShardedCustomerRepository}.
 *
 * <p>South is {@code @Primary} purely so that anything injecting a bare {@code MongoTemplate}
 * resolves rather than failing on ambiguity; shard-aware code always qualifies.
 *
 * <p><b>Why the clients are explicit beans.</b> Both stores are Atlas clusters shared by the
 * whole team, so the driver defaults are wrong for us in two ways, and neither is visible until
 * it fails:
 * <ul>
 *   <li>The default pool is 100 connections per client. A free cluster admits 500 in total, so
 *       five developers running the app at once sit exactly on the ceiling before Compass or a
 *       test run is counted. Capped at {@code maxPoolSize} instead.</li>
 *   <li>The default server-selection timeout is 30 seconds, so a bad network or a missing IP
 *       allowlist entry looks like a hang rather than an error. Shortened so it fails loudly.</li>
 * </ul>
 * Declaring the clients as beans also means Spring closes them on shutdown, and it suppresses
 * Spring Boot's auto-configured {@code MongoClient}, which would otherwise point at
 * {@code localhost:27017} and mislead whoever is reading a stack trace.
 */
@Configuration
public class ShardMongoConfig {

	public static final String SOUTH_TEMPLATE = "southMongoTemplate";
	public static final String NORTH_TEMPLATE = "northMongoTemplate";

	private static final String SOUTH_CLIENT = "southMongoClient";
	private static final String NORTH_CLIENT = "northMongoClient";

	@Bean(SOUTH_CLIENT)
	public MongoClient southMongoClient(SbpProperties properties) {
		return buildClient(properties.getMongo(), properties.getMongo().getSouth().getUri());
	}

	@Bean(NORTH_CLIENT)
	public MongoClient northMongoClient(SbpProperties properties) {
		return buildClient(properties.getMongo(), properties.getMongo().getNorth().getUri());
	}

	@Bean(SOUTH_TEMPLATE)
	@Primary
	public MongoTemplate southMongoTemplate(@Qualifier(SOUTH_CLIENT) MongoClient client,
			SbpProperties properties, MongoMappingContext mappingContext,
			MongoCustomConversions conversions) {

		return buildTemplate(client, properties.getMongo().getSouth().getUri(), mappingContext,
				conversions);
	}

	@Bean(NORTH_TEMPLATE)
	public MongoTemplate northMongoTemplate(@Qualifier(NORTH_CLIENT) MongoClient client,
			SbpProperties properties, MongoMappingContext mappingContext,
			MongoCustomConversions conversions) {

		return buildTemplate(client, properties.getMongo().getNorth().getUri(), mappingContext,
				conversions);
	}

	/**
	 * Connection-string settings are applied first so a URI can still carry per-developer
	 * overrides; the explicit calls after it are the ones we insist on.
	 */
	private MongoClient buildClient(SbpProperties.Mongo mongo, String uri) {
		ConnectionString connectionString = new ConnectionString(uri);

		MongoClientSettings settings = MongoClientSettings.builder()
				.applyConnectionString(connectionString)
				.applyToConnectionPoolSettings(pool -> pool.maxSize(mongo.getMaxPoolSize()))
				.applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(
						mongo.getServerSelectionTimeoutSeconds(), TimeUnit.SECONDS))
				.build();

		return MongoClients.create(settings);
	}

	/**
	 * Both templates share the auto-configured mapping context, so entity metadata — including
	 * {@code spring.data.mongodb.auto-index-creation} — applies identically to each shard.
	 */
	private MongoTemplate buildTemplate(MongoClient client, String uri,
			MongoMappingContext mappingContext, MongoCustomConversions conversions) {

		String database = new ConnectionString(uri).getDatabase();
		if (database == null || database.isBlank()) {
			throw new IllegalStateException(
					"Mongo URI is missing a database name. Expected .../sbp_north or .../sbp_south "
							+ "before the query string — see docs/MONGODB_SETUP.md step B2.");
		}

		MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(client, database);

		MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory),
				mappingContext);
		converter.setCustomConversions(conversions);
		// Drop the "_class" discriminator: these documents are read back as one known type.
		converter.setTypeMapper(new DefaultMongoTypeMapper(null));
		converter.afterPropertiesSet();

		return new MongoTemplate(factory, converter);
	}
}