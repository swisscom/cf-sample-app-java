package com.swisscom.cloud.cloudfoundry.sampleapp.java;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.SparkBase.port;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import redis.clients.jedis.Jedis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ProductService {

    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;

	private static final Info INFO = new Info("I am awesome!", "1.0.0", System.getenv().get("APP_MODE"));

    public static void main( String[] args) {

    	port(getCloudAssignedPort());

    	ProductRepository productRepository = getProductRepository();

        get("/", (request, response) -> {
            response.status(HTTP_OK);
            response.type("application/json");
            return mapToJson(INFO);
        });

        post("/products", (request, response) -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Product product = mapper.readValue(request.body(), Product.class);
                if (! product.isValid()) {
                    response.status(HTTP_BAD_REQUEST);
                    return "Product input invalid";
                }
                long id = productRepository.add(product);
                response.status(HTTP_OK);
                response.type("application/json");
                return id;
            } catch (JsonParseException exception) {
                response.status(HTTP_BAD_REQUEST);
                return "Json payload invalid";
            }
        });

        get("/products", (request, response) -> {
            response.status(HTTP_OK);
            response.type("application/json");
            return mapToJson(productRepository.findAll());
        });
    }

    private static String mapToJson(Object data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, data);
            return writer.toString();
        } catch (IOException exception){
            throw new RuntimeException("Could not write JSON response payload", exception);
        }
    }

    private static int getCloudAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 4567; //return default port if cloud port isn't set (i.e. on localhost)
    }

    private static ProductRepository getProductRepository() {
    	ProcessBuilder processBuilder = new ProcessBuilder();
        String servicesJson = processBuilder.environment().get("VCAP_SERVICES");
        if (servicesJson != null) {
    	 	Map<String,Object> redisCredentials = getRedisCredentials(servicesJson);
           	return new RedisProductRepository(redisCredentials);
        } else {
        	return new SimpleProductRepository();
        }
    }

	@SuppressWarnings("unchecked")
    private static Map<String,Object> getRedisCredentials(String servicesJson) {
        ObjectMapper mapper = new ObjectMapper();
        if (servicesJson != null) {
        	try {
				Map<String,Object> services = mapper.readValue(servicesJson, new TypeReference<Map<String,Object>>() { });
				List<Map<String,Object>> redisServices = (List<Map<String, Object>>) services.get("redis");
				for (Map<String,Object> redisService : redisServices) {
					// It is assumed that only one Redis service is bound to this app. Evaluate the name property
					// of the credentials in case multiple Redis services are bound to an app
					return (Map<String, Object>) redisService.get("credentials");
				}
        	} catch (Exception exception) {
				throw new RuntimeException("Redis service declaration not found", exception);
			}
        }
		throw new RuntimeException("Redis service declaration not found");
    }

    public static class Info {

    	private String status;
        private String version;
        private String appMode;

        public Info(String status, String version, String appMode) {
        	this.status = status;
        	this.version = version;
        	this.appMode = appMode;
        }

        public String getStatus() {
        	return status;
        }

        public String getVersion() {
        	return version;
        }

        public String getAppMode() {
        	return appMode;
        }
    }

    public static class Product {

    	private long id;
        private String description;
        private BigDecimal price;

        public long getId() {
        	return id;
        }

        public void setId(long id) {
        	this.id = id;
        }

        public String getDescription() {
        	return description;
        }

        public BigDecimal getPrice() {
        	return price;
        }

        @JsonIgnore
        public boolean isValid() {
        	if (description == null || description.isEmpty()) {
        		return false;
        	}
        	if (price == null) {
        		return false;
        	}
        	return true;
        }
    }

    public interface ProductRepository {
    	public long add(Product product);
    	public Collection<Product> findAll();
    }

    public static class SimpleProductRepository implements ProductRepository {

    	private Map<Long,Product> products = new HashMap<Long,Product>();
    	private long id;

    	@Override
    	public long add(Product product) {
    		product.setId(nextId());
    		products.put(product.getId(), product);
    		return product.getId();
    	}

    	@Override
    	public Collection<Product> findAll() {
    		return products.values();
    	}

    	private long nextId() {
    		return ++id;
    	}
    }

    public static class RedisProductRepository implements ProductRepository {

    	private Jedis jedis;
    	private ObjectMapper mapper;

    	public RedisProductRepository(Map<String,Object> redisCredentials) {
    		jedis = new Jedis((String) redisCredentials.get("host"), (int) redisCredentials.get("port"));
    		jedis.auth((String) redisCredentials.get("password"));
    		mapper = new ObjectMapper();
    	}

    	public long add(Product product) {
    		product.setId(nextId());
    		jedis.rpush("products", mapToJson(product));
    		return product.getId();
    	}

    	public Collection<Product> findAll() {
    		return jedis.lrange("products", 0, jedis.llen("products")).stream()
    			.map(productJson -> mapToProduct(productJson))
    			.collect(Collectors.toList());
    	}

    	private Long nextId() {
    		return jedis.incr("productid");
    	}

    	private String mapToJson(Product product) {
      		try {
 				return mapper.writeValueAsString(product);
 			} catch (JsonProcessingException e) {
 				throw new RuntimeException("Product cannot be serialized as JSON");
 			}
    	}

    	private Product mapToProduct(String productJson) {
    		try {
				return mapper.readValue(productJson, Product.class);
			} catch (IOException exception) {
				throw new RuntimeException("Product cannot be deserialized from JSON");
			}
    	}
    }
}
