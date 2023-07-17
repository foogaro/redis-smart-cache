package com.redis.smartcache.core.config;

import java.util.Objects;

public class Config {

	public static final String DEFAULT_NAME = "smartcache";
	public static final int DEFAULT_QUERY_CACHE_CAPACITY = 10000;

	private String name = DEFAULT_NAME;
	private int queryCacheCapacity = DEFAULT_QUERY_CACHE_CAPACITY;

	private DriverConfig driver = new DriverConfig();
	private RedisConfig redis = new RedisConfig();
	private RulesetConfig ruleset = new RulesetConfig();
	private MetricsConfig metrics = new MetricsConfig();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getQueryCacheCapacity() {
		return queryCacheCapacity;
	}

	public void setQueryCacheCapacity(int capacity) {
		this.queryCacheCapacity = capacity;
	}

	public DriverConfig getDriver() {
		return driver;
	}

	public void setDriver(DriverConfig driver) {
		this.driver = driver;
	}

	public RulesetConfig getRuleset() {
		return ruleset;
	}

	public void setRuleset(RulesetConfig ruleset) {
		this.ruleset = ruleset;
	}

	public RedisConfig getRedis() {
		return redis;
	}

	public void setRedis(RedisConfig redis) {
		this.redis = redis;
	}

	public MetricsConfig getMetrics() {
		return metrics;
	}

	public void setMetrics(MetricsConfig metrics) {
		this.metrics = metrics;
	}

	@Override
	public int hashCode() {
		return Objects.hash(redis.getUri(), name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Config other = (Config) obj;
		return Objects.equals(redis.getUri(), other.redis.getUri()) && Objects.equals(name, other.name);
	}

}
