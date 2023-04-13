package com.example2.create.stream.config;

import com.example2.create.stream.config.props.FileTransferCreateProperties;
import com.example2.create.stream.service.FileTransferCreate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dispatcher.RoundRobinLoadBalancingStrategy;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.handler.advice.ErrorMessageSendingRecoverer;
import org.springframework.integration.json.ObjectToJsonTransformer;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.dsl.KafkaProducerMessageHandlerSpec;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.support.DefaultErrorMessageStrategy;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.support.RetryTemplate;

@RequiredArgsConstructor
@Configuration
@IntegrationComponentScan (
	basePackageClasses =  	FileTransferCreate.class 
)
public class FileTransferCreateConfig { 

	public static final String FILE_TRANSFER_CREATE_CHANNEL = "fileTransferCreateInputChannel";
	public static final String CONSUMER_MESSAGE_CHANNEL_ID = "file.transfer.create.messageHandler";
	public static final String ERROR_MESSAGE_CHANNEL = "file.transfer.create.errorChannel";
	private final KafkaProperties defaultKafkaProperties;
	private final FileTransferCreateProperties kafkaProperties; 

	@ConditionalOnProperty (
		name =  	"file.transfer.create.enabled",
		havingValue =  	"false" 
	)
	@Bean
	public IntegrationFlow stubFileTransferCreateInputFlow() {
		return IntegrationFlows.from(FILE_TRANSFER_CREATE_CHANNEL).nullChannel(); 
	}
	
	@Bean (
		FILE_TRANSFER_CREATE_CHANNEL 
	)
	public MessageChannel fileTransferCreateChannel() {
		return MessageChannels.direct().get(); 
	}
	
	@Bean (
		ERROR_MESSAGE_CHANNEL 
	)
	public MessageChannel localErrorChannel() {
		return new DirectChannel(new RoundRobinLoadBalancingStrategy()); 
	}
	
	@Bean
	@ConditionalOnProperty (
		name =  	"file.transfer.create.enabled",
		havingValue =  	"true" 
	)
	public IntegrationFlow fileTransferCreateInputFlow() {
		return IntegrationFlows.from(FILE_TRANSFER_CREATE_CHANNEL) 
			.transform(Transformers.toJson(ObjectToJsonTransformer.ResultType.STRING)) 
			.publishSubscribeChannel(c -> c.subscribe(f -> f.handle(messageHandler(producerFactory(), kafkaProperties.getTopic())))) 
			.get(); 
	}
	
	private KafkaProducerMessageHandlerSpec<Integer, String, ?> messageHandler(
		ProducerFactory<Integer, String> producerFactory,
		String topic
	) {
		return Kafka.outboundChannelAdapter(producerFactory)
			.messageKey(m -> m.getHeaders().get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER))
			.headerMapper(new DefaultKafkaHeaderMapper())
			.topic(topic)
			.configureKafkaTemplate(t -> t.id("kafkaTemplate:" + topic)); 
	}
	
	private ProducerFactory<Integer, String> producerFactory() {
		var map = defaultKafkaProperties.buildProducerProperties(); 
		map.putAll(kafkaProperties.buildCommonProperties()); 
		map.putAll(kafkaProperties.buildProducerProperties()); 
		return new DefaultKafkaProducerFactory<>(map); 
	}
	
	@Bean
	@ConditionalOnProperty (
		name =  	"file.transfer.create.enabled",
		havingValue =  	"true" 
	)
	public IntegrationFlow fileTransferCreateListenerStream() {
		return IntegrationFlows 
			.from(Kafka 
				.messageDrivenChannelAdapter(consumerFactory(), 
					KafkaMessageDrivenChannelAdapter.ListenerMode.record, 
					kafkaProperties.getTopic() 
				) 
				.configureListenerContainer(container -> container 
					.ackMode(ContainerProperties.AckMode.RECORD) 
					.syncCommits(true) 
					.id(CONSUMER_MESSAGE_CHANNEL_ID) 
					.get()) 
				.recoveryCallback(new ErrorMessageSendingRecoverer(localErrorChannel(), new DefaultErrorMessageStrategy())) 
				.retryTemplate(RetryTemplate.builder() 
					.maxAttempts(kafkaProperties.getBackoffRetries()) 
					.exponentialBackoff(kafkaProperties.getBackoffInitial(), 
						kafkaProperties.getBackoffMultiplier(), 
						kafkaProperties.getBackoffMax()) 
					.build()) 
				.get()) 
			.transform(Transformers.fromJson()) 
			.logAndReply(); 
	}
	
	private ConsumerFactory<Integer, String> consumerFactory() {
		var map = defaultKafkaProperties.buildConsumerProperties(); 
		map.putAll(kafkaProperties.buildCommonProperties()); 
		map.putAll(kafkaProperties.buildConsumerProperties()); 
		return new DefaultKafkaConsumerFactory<>(map); 
	}
	
	@Bean
	public ObjectMapper headerMapper() {
		var mapper =  JsonMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES).build(); 
		mapper.registerModule(new JavaTimeModule()); 
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false); 
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); 
		return mapper; 
	} 

}