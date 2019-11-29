import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import util.ByteConverter;
import util.PrimeUtil;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static util.RabbitMQUtils.*;

public class Client {

    private Channel channel;
    private String name;

    Client(@NotNull String rabbitMQHost, @NotNull String rabbitMQUser, @NotNull String rabbitMQPass, @NotNull Integer rabbitMQPort, @NotNull String clientName) throws IOException {
        name = clientName;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQHost);
        factory.setPort(rabbitMQPort);
        factory.setUsername(rabbitMQUser);
        factory.setPassword(rabbitMQPass);

        initializeRabbitMQConnection(factory);
        listenForTasks();
        notifyProducer();
    }

    private void initializeRabbitMQConnection(@NotNull ConnectionFactory factory) throws IOException {
        try {
            System.out.println("Creating connection...");
            Connection connection = factory.newConnection();
            System.out.println("Connection created successfully");

            System.out.println("Creating channel...");
            channel = connection.createChannel();
            System.out.println("Channel created successfully with number " + channel.getChannelNumber());

            CreateDefaultExchanges(channel);
            CreateDefaultQueues(channel);
            createClientQueue(channel);
        }
        catch (TimeoutException e) {
            System.out.println("Timeout while trying to connect to the RabbitMQ server");
        }
    }

    private void notifyProducer() throws IOException {
        System.out.println("Notifying publisher of creation...");
        channel.basicPublish(CONSUMER_EXCHANGE_NAME, Queue.CONSUMER_REGISTRATION_QUEUE.getName(), null, name.getBytes());
    }

    private void createClientQueue(@NotNull Channel channel) throws IOException{
        System.out.println("Declaring custom queue for data exchange...");
        //queueDeclare(name, durable, exclusive, autoDelete, arguments)
        channel.queueDeclare(getProductionQueueName(), false, false, true, null);
        System.out.println("Custom queue declared successfully");

        System.out.println("Binding custom queue for data exchange...");
        channel.queueBind(getProductionQueueName(), PRODUCER_EXCHANGE_NAME, getProductionQueueName());
        System.out.println("Binding of custom queue completed successfully");
    }

    private void listenForTasks() throws IOException {
        channel.basicConsume(getProductionQueueName(), true, name,
                new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                        Integer numberToCheck = Integer.valueOf(new String(body));
                        executeTask(numberToCheck);
                    }
                });
    }

    private void executeTask(Integer numberToCheck) throws IOException {
        boolean isPrime = PrimeUtil.isPrimeNumber(numberToCheck);
        ClientReturn clientReturn = new ClientReturn();
        clientReturn.isPrime = isPrime;
        clientReturn.numberToCheck = numberToCheck;
        channel.basicPublish(CONSUMER_EXCHANGE_NAME, Queue.CONSUMER_DATA_RETURN_QUEUE.getName(), null, SerializationUtils.serialize(clientReturn));
    }

    private String getProductionQueueName() {
        return Queue.CONSUMER_PRODUCTION_QUEUE.getName() + "_" + name;
    }
}
