package com.swisscom.cloud.cloudfoundry.sampleapp.java;
 
import static spark.Spark.get;
import static spark.Spark.post;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ProductService {

    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;
    
	private static final Info INFO = new Info("Your Java sample app is up and running !", "1.0.00");
    
    public static void main( String[] args) {
    	
        ProductRepository productRepository = new ProductRepository();
        
        get("/", (request, response) -> {
            response.status(HTTP_OK);
            response.type("application/json");
            return dataToJson(INFO);
        });
        
        post("/products", (request, response) -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Product product = mapper.readValue(request.body(), Product.class);
                if (! product.isValid()) {
                    response.status(HTTP_BAD_REQUEST);
                    return "Product input invalid";
                }
                int id = productRepository.add(product);
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
            return dataToJson(productRepository.findAll());
        });
    }

    public static String dataToJson(Object data) {
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
    
    public static class Info {
      	 
    	private String status;
        private String version;
        
        public Info(String status, String version) {
        	this.status = status;
        	this.version = version;
        }
       
        public String getStatus() {
        	return status;
        }
        
        public String getVersion() {
        	return version;
        }
    }
    
    public static class Product {
   	 
    	private int id;
        private String description;
        private BigDecimal price;
       
        public int getId() {
        	return id;
        }
        
        public void setId(int id) {
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
    
    public static class ProductRepository {
    	
    	private Map<Integer,Product> products = new HashMap<Integer,Product>();
    	private int id = 0;
    	
    	public int add(Product product) {
    		product.setId(nextId());
    		this.products.put(product.getId(), product);
    		return product.getId();
    	}
    	
    	public Collection<Product> findAll() {
    		return products.values();
    	}
    	
    	private int nextId() {
    		return ++id;
    	}
    }
}