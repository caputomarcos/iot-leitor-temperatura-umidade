package com.leitor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Classe responsável pela integração entre um sensor conectado via porta serial
 * e um broker MQTT para publicação dos dados coletados.
 */
public class SensorWorker {

    private static final Logger logger = LoggerFactory.getLogger(SensorWorker.class);

    // Porta serial utilizada para comunicação com o sensor
    private final SerialPort portaSerial;

    // Cliente MQTT para publicação de mensagens
    private MqttClient mqttClient;

    // Configurações de conexão MQTT
    private MqttConnectOptions connOpts;

    // Listener para eventos da porta serial
    private SerialPortDataListener serialPortDataListener;

    // Configuração de retry para publicação MQTT
    private final Retry retry;

    // Constantes de configuração MQTT
    private static final String MQTT_BROKER = "tcp://localhost:1883";
    private static final String TOPICO_TEMPERATURA = "sensores/temperatura";
    private static final String TOPICO_UMIDADE = "sensores/umidade";

    /**
     * Construtor principal da classe para uso em produção.
     *
     * @param portaSerial Objeto da porta serial configurado.
     * @param clientId Identificador único para o cliente MQTT.
     * @throws MqttException Caso ocorra algum erro na inicialização do cliente MQTT.
     */
    public SensorWorker(SerialPort portaSerial, String clientId) throws MqttException {
        this.portaSerial = portaSerial;
        this.mqttClient = new MqttClient(MQTT_BROKER, clientId, new MemoryPersistence());

        // Configurações de conexão MQTT
        this.connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);
        connOpts.setConnectionTimeout(10);

        // Tentativa de conexão ao broker MQTT
        try {
            mqttClient.connect(connOpts);
            logger.info("Conectado ao broker MQTT");
            System.out.println("Conectado ao broker MQTT");
        } catch (MqttException e) {
            logger.error("Erro ao conectar no MQTT: {}", e.getMessage(), e);
            System.out.print("Erro ao conectar no MQTT: " + e.getMessage());
        }

        // Configuração de política de retry para publicação MQTT
        RetryConfig retryConfig = RetryConfig.<Void>custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .build();
        this.retry = Retry.of("mqttRetry", retryConfig);
    }

    /**
     * Construtor alternativo para testes, permitindo injetar um mock de MqttClient.
     *
     * @param portaSerial Objeto da porta serial configurado.
     * @param mqttClient Cliente MQTT mockado para testes.
     */
    public SensorWorker(SerialPort portaSerial, MqttClient mqttClient) {
        this.portaSerial = portaSerial;
        this.mqttClient = mqttClient;
        this.retry = RetryRegistry.ofDefaults().retry("mqttRetry");
    }

    /**
     * Inicia a leitura de dados da porta serial e configura os listeners.
     */
    public void iniciarLeitura() {
        if (portaSerial.openPort()) {
            logger.info("Porta serial [{}] aberta com sucesso.", portaSerial.getSystemPortName());
            System.out.println("Porta serial [" + portaSerial.getSystemPortName() + "] aberta com sucesso.");

            // Configura o listener para eventos de dados disponíveis na porta serial
            synchronized (this) {
                serialPortDataListener = new SerialPortDataListener() {
                    @Override
                    public int getListeningEvents() {
                        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                    }

                    @Override
                    public void serialEvent(SerialPortEvent event) {
                        if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                            byte[] buffer = new byte[portaSerial.bytesAvailable()];
                            portaSerial.readBytes(buffer, buffer.length);
                            String data = new String(buffer, StandardCharsets.UTF_8);
                            processarDados(data);
                        }
                    }
                };
            }

            portaSerial.addDataListener(serialPortDataListener);
        } else {
            logger.warn("Não foi possível abrir a porta serial [{}].", portaSerial.getSystemPortName());
            System.out.println("Não foi possível abrir a porta serial [" + portaSerial.getSystemPortName() + "].");
        }
    }

    /**
     * Encerra a leitura da porta serial e desconecta o cliente MQTT.
     */
    public void pararLeitura() {
        if (portaSerial.isOpen()) {
            portaSerial.removeDataListener();
            portaSerial.closePort();
            logger.info("Porta serial [{}] fechada.", portaSerial.getSystemPortName());
            System.out.println("Porta serial [" + portaSerial.getSystemPortName() + "] fechada.");
        }
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                logger.info("Conexão MQTT encerrada.");
                System.out.println("Conexão MQTT encerrada.");
            }
        } catch (MqttException e) {
            logger.error("Erro ao encerrar conexão MQTT: {}", e.getMessage(), e);
            System.out.println("Erro ao encerrar conexão MQTT: " + e.getMessage());
        }
    }

    /**
     * Processa os dados recebidos pela porta serial e publica no MQTT quando aplicável.
     *
     * @param dados Dados recebidos pela porta serial.
     */
    void processarDados(String dados) {
        logger.info("Dados recebidos na porta serial: {}", dados);
        System.out.println("Dados recebidos na porta serial: " + dados);
        try {
            String[] linhas = dados.split("\\r?\\n");
            for (String linha : linhas) {
                if (linha.startsWith("TEMP:")) {
                    String temperatura = linha.substring(5).trim();
                    publicarNoMQTT(TOPICO_TEMPERATURA, temperatura);
                } else if (linha.startsWith("UMID:")) {
                    String umidade = linha.substring(5).trim();
                    publicarNoMQTT(TOPICO_UMIDADE, umidade);
                } else if (!linha.trim().isEmpty()) {
                    logger.warn("Dados desconhecidos recebidos: {}", linha);
                    System.out.println("Dados desconhecidos recebidos: " + linha);
                }
            }
        } catch (Exception e) {
            logger.error("Erro ao processar dados: {}", e.getMessage(), e);
            System.out.println("Erro ao processar dados: " + e.getMessage());
        }
    }

    /**
     * Publica uma mensagem no tópico MQTT especificado, com política de retry.
     *
     * @param topico Tópico MQTT onde a mensagem será publicada.
     * @param mensagem Conteúdo da mensagem a ser publicada.
     */
    public void publicarNoMQTT(String topico, String mensagem) {
        Runnable publicarRunnable = () -> {
            try {
                doPublicar(topico, mensagem);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        };
        try {
            Retry.decorateRunnable(retry, publicarRunnable).run();
        } catch (RuntimeException ex) {
            logger.error("Erro ao publicar no MQTT: {}", ex.getMessage(), ex);
            System.out.println("Erro ao publicar no MQTT: " + ex.getMessage());
        }
    }

    /**
     * Lógica principal para publicar uma mensagem no MQTT.
     *
     * @param topico Tópico MQTT onde a mensagem será publicada.
     * @param mensagem Conteúdo da mensagem a ser publicada.
     * @throws MqttException Caso ocorra um erro ao publicar.
     */
    private void doPublicar(String topico, String mensagem) throws MqttException {
        if (!mqttClient.isConnected()) {
            logger.warn("Cliente MQTT desconectado; tentando reconexão...");
            System.out.println("Cliente MQTT desconectado; tentando reconexão...");
            mqttClient.reconnect();
        }
        if (!mqttClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage msg = new MqttMessage(mensagem.getBytes(StandardCharsets.UTF_8));
        msg.setQos(2);
        mqttClient.publish(topico, msg);
        logger.info("Publicado no tópico [{}]: {}", topico, mensagem);
        System.out.println("Publicado no tópico [" + topico + "]: " + mensagem);
    }

    /**
     * Retorna o listener configurado para a porta serial.
     *
     * @return O listener configurado.
     * @throws IllegalStateException Caso o listener não tenha sido inicializado.
     */
    public SerialPortDataListener getSerialPortDataListener() {
        if (serialPortDataListener == null) {
            throw new IllegalStateException("SerialPortDataListener não foi inicializado.");
        }
        return serialPortDataListener;
    }

    /**
     * Configura um listener para eventos da porta serial.
     *
     * @param listener Objeto listener a ser configurado.
     */
    public void setSerialPortDataListener(SerialPortDataListener listener) {
        this.serialPortDataListener = listener;
    }

    /**
     * Metodo principal para execucao do programa.
     *
     * @param args Argumentos de linha de comando.
     */
    public static void main(String[] args) {
        SerialPort portaSerial = SerialPort.getCommPort("/dev/pts/3");
        portaSerial.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        portaSerial.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        try {
            SensorWorker worker = new SensorWorker(portaSerial, "SensorWorkerClient");

            // Adiciona o shutdown hook para encerramento suave
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Encerrando o worker...");
                worker.pararLeitura();
            }));

            // Inicia a leitura em uma nova thread
            Thread workerThread = new Thread(worker::iniciarLeitura);
            workerThread.start();

            // Aguarda o comando de encerramento
            System.out.println("Digite 'exit' e pressione Enter para encerrar:");
            try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
                while (scanner.hasNext()) {
                    String input = scanner.nextLine();
                    if ("exit".equalsIgnoreCase(input)) {
                        System.out.println("Encerrando o programa...");
                        worker.pararLeitura(); // Encerra o worker
                        break;
                    }
                }
            }

            // Aguarda o encerramento da thread
            workerThread.join();
        } catch (MqttException | InterruptedException e) {
            logger.error("Erro: {}", e.getMessage(), e);
            System.err.println("Erro: " + e.getMessage());
        }
    }
}
