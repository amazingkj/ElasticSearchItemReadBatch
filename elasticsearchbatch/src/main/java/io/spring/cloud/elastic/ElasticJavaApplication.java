package io.spring.cloud.elastic;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.task.configuration.EnableTask;

@EnableTask
@EnableBatchProcessing
@SpringBootApplication
//@IntegrationComponentScan
public class ElasticJavaApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(ElasticJavaApplication.class)
				.web(WebApplicationType.NONE) // .REACTIVE, .SERVLET
				.run(args);
	}




}
