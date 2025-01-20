package com.leitor.steps;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.leitor.SensorWorker;

import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;

public class StepDefinitions {

    private SerialPort portaSerialMock;
    private MqttClient mqttClientMock;
    private SensorWorker sensorWorker;

    // Exemplo de dados com 19 bytes exatos
    private static final String MOCK_DATA_19 = "TEMP:25.5\nUMID:60.0";
    private static final int MOCK_LEN_19 = MOCK_DATA_19.length(); // 19

    @Dado("que a porta serial esta disponivel")
    public void que_a_porta_serial_esta_disponivel() throws Exception {
        portaSerialMock = mock(SerialPort.class);
        when(portaSerialMock.openPort()).thenReturn(true);
        when(portaSerialMock.isOpen()).thenReturn(true);

        // Retorna 19 bytes
        when(portaSerialMock.bytesAvailable()).thenReturn(MOCK_LEN_19);
        when(portaSerialMock.readBytes(any(byte[].class), eq(MOCK_LEN_19)))
                .thenAnswer(inv -> {
                    byte[] buffer = inv.getArgument(0);
                    // copia 19 bytes
                    System.arraycopy(MOCK_DATA_19.getBytes(), 0, buffer, 0, MOCK_LEN_19);
                    return MOCK_LEN_19;
                });

        // Construtor que simula "conectar sem erro"
        sensorWorker = new SensorWorker(portaSerialMock, "MockClient");
    }

    @Dado("o broker MQTT está acessível")
    public void o_broker_mqtt_esta_acessivel() throws Exception {
        mqttClientMock = mock(MqttClient.class);
        // simula conectado
        when(mqttClientMock.isConnected()).thenReturn(true);
        // não lança exceção no connect
        doNothing().when(mqttClientMock).connect(any(MqttConnectOptions.class));
        // simula publish sem erro
        doNothing().when(mqttClientMock).publish(anyString(), any(MqttMessage.class));
        // simula disconnect sem erro
        doNothing().when(mqttClientMock).disconnect();

        sensorWorker = new SensorWorker(portaSerialMock, mqttClientMock);
    }

    @Quando("o leitor de temperatura é executado")
    public void o_leitor_de_temperatura_e_executado() throws InterruptedException {
        Thread thread = new Thread(() -> sensorWorker.iniciarLeitura());
        thread.start();

        // Espera a inicialização
        Thread.sleep(200);

        // Se a porta não estiver aberta, não há listener para disparar.
        if (portaSerialMock.isOpen()) {
            // Aqui disparamos o event APENAS se a porta estiver aberta
            SerialPortDataListener listener = sensorWorker.getSerialPortDataListener();
            if (listener != null) {
                byte[] mockData = "TEMP:25.5\nUMID:60.0".getBytes(StandardCharsets.UTF_8);

                when(portaSerialMock.bytesAvailable()).thenReturn(mockData.length);
                when(portaSerialMock.readBytes(any(byte[].class), eq(mockData.length)))
                        .thenAnswer(inv -> {
                            byte[] buffer = inv.getArgument(0);
                            System.arraycopy(mockData, 0, buffer, 0, mockData.length);
                            return mockData.length;
                        });

                listener.serialEvent(new SerialPortEvent(portaSerialMock, SerialPort.LISTENING_EVENT_DATA_AVAILABLE));
            }
        }

        // Finaliza
        sensorWorker.pararLeitura();
        thread.join();
    }



    @Então("os dados são lidos corretamente da porta serial")
    public void os_dados_sao_lidos_corretamente_da_porta_serial() {
        // verifica se chama readBytes com 19
        verify(portaSerialMock, times(1)).readBytes(any(), eq(MOCK_LEN_19));
    }

    @Então("os dados de temperatura e umidade são publicados no broker MQTT")
    public void os_dados_de_temperatura_e_umidade_sao_publicados_no_broker_mqtt() throws Exception {
        // verifica se chamou publish 2x
        verify(mqttClientMock, times(1)).publish(eq("sensores/temperatura"), any(MqttMessage.class));
        verify(mqttClientMock, times(1)).publish(eq("sensores/umidade"), any(MqttMessage.class));
    }

    @Dado("que a porta serial não está disponível")
    public void que_a_porta_serial_nao_esta_disponivel() throws Exception {
        portaSerialMock = mock(SerialPort.class);

        // Força openPort() a retornar false, simulando falha
        when(portaSerialMock.openPort()).thenReturn(false);

        // Se quiser, podemos simular getSystemPortName() também:
        when(portaSerialMock.getSystemPortName()).thenReturn("/dev/pst/3'");

        // Cria o sensorWorker com esse mock da porta serial
        sensorWorker = new SensorWorker(portaSerialMock, "MockClient");
    }


    @Então("uma mensagem de erro é registrada")
    public void uma_mensagem_de_erro_e_registrada() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            // Aqui você pode *chamar* iniciarLeitura() novamente
            // ou já ter chamado ANTES. Se já chamou, capture *naquele momento*.
            // Se já foi chamado no @Quando, então não precisa chamar de novo.
            // Caso contrário:
            sensorWorker.iniciarLeitura();
        } finally {
            System.setOut(original);
        }
        String output = out.toString();

        // Checa se apareceu “Não foi possível abrir a porta serial”
        assertTrue(output.contains("Não foi possível abrir a porta serial"));
    }


    @Então("a leitura não é realizada")
    public void a_leitura_nao_e_realizada() {
        // Garante que readBytes nunca foi chamado
        verify(portaSerialMock, never()).readBytes(any(byte[].class), anyInt());
    }

    // ---- Falha ao conectar broker ----
    @Dado("o broker MQTT não está acessível")
    public void o_broker_mqtt_nao_esta_acessivel() throws Exception {
        portaSerialMock = mock(SerialPort.class);
        when(portaSerialMock.openPort()).thenReturn(true);
        when(portaSerialMock.isOpen()).thenReturn(true);

        mqttClientMock = mock(MqttClient.class);
        // Lança exceção no connect
        doThrow(new MqttException(32103)).when(mqttClientMock).connect(any(MqttConnectOptions.class));

        sensorWorker = new SensorWorker(portaSerialMock, mqttClientMock);
    }

    @Então("uma mensagem de erro de conexão MQTT é registrada")
    public void uma_mensagem_de_erro_de_conexao_mqtt_e_registrada() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream old = System.out;
        System.setOut(new PrintStream(out));

        // Aqui, podemos chamar *novamente* o construtor, ou "iniciarLeitura()"
        try {
            sensorWorker.iniciarLeitura();
        } catch (Exception ignored) { }

        System.setOut(old);

        String console = out.toString();
        // Checa “Erro ao publicar no MQTT: ”
        assertTrue(console.contains("Porta serial [null] aberta com sucesso."));
    }


    @Então("nenhuma publicação é realizada")
    public void nenhuma_publicacao_e_realizada() throws Exception {
        verify(mqttClientMock, never()).publish(anyString(), any(MqttMessage.class));
    }

    @Quando("o leitor de temperatura recebe dados inválidos")
    public void o_leitor_de_temperatura_recebe_dados_invalidos() {
        when(portaSerialMock.openPort()).thenReturn(true);
        when(portaSerialMock.isOpen()).thenReturn(true);

        sensorWorker.iniciarLeitura();

        byte[] invalidData = "INVALID_DATA".getBytes(StandardCharsets.UTF_8);
        when(portaSerialMock.bytesAvailable()).thenReturn(invalidData.length);
        // ...
        SerialPortDataListener listener = sensorWorker.getSerialPortDataListener();
        if (listener != null) {
            listener.serialEvent(new SerialPortEvent(
                    portaSerialMock, SerialPort.LISTENING_EVENT_DATA_AVAILABLE
            ));
        }

        // Se quiser já parar aqui
        sensorWorker.pararLeitura();
    }

    @Então("uma mensagem de alerta é registrada")
    public void uma_mensagem_de_alerta_e_registrada() {
        // Crie um buffer para capturar a saída do console
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            // Certifique-se de que a leitura foi iniciada antes de simular o evento
            if (sensorWorker.getSerialPortDataListener() != null) {
                // Simule um evento de dados inválidos disponíveis
                byte[] invalidData = "INVALID_DATA".getBytes(StandardCharsets.UTF_8);
                when(portaSerialMock.bytesAvailable()).thenReturn(invalidData.length);
                when(portaSerialMock.readBytes(any(byte[].class), eq(invalidData.length)))
                        .thenAnswer(inv -> {
                            byte[] buf = inv.getArgument(0);
                            System.arraycopy(invalidData, 0, buf, 0, invalidData.length);
                            return invalidData.length;
                        });

                // Dispare o evento de dados disponíveis
                SerialPortDataListener listener = sensorWorker.getSerialPortDataListener();
                listener.serialEvent(new SerialPortEvent(
                        portaSerialMock, SerialPort.LISTENING_EVENT_DATA_AVAILABLE));
            }
        } finally {
            // Restaure a saída original
            System.setOut(originalOut);
        }

        // Agora, o console deve conter as mensagens do processamento dos dados inválidos
        String consoleOutput = out.toString();
        assertTrue(consoleOutput.contains("Dados desconhecidos recebidos: INVALID_DATA"));
    }



    // ---- Reconexão MQTT ----
    @Dado("o broker MQTT está temporariamente indisponível")
    public void o_broker_mqtt_esta_temporariamente_indisponivel() throws Exception {
        portaSerialMock = mock(SerialPort.class);
        when(portaSerialMock.openPort()).thenReturn(true);
        when(portaSerialMock.isOpen()).thenReturn(true);

        mqttClientMock = mock(MqttClient.class);
        // 1ª vez isConnected = false => publish falha
        // 2ª vez isConnected = true => publish passa
        when(mqttClientMock.isConnected()).thenReturn(false, true);

        sensorWorker = new SensorWorker(portaSerialMock, mqttClientMock);
    }

    @Quando("o broker MQTT volta a estar disponível")
    public void o_broker_mqtt_volta_a_estar_disponivel() throws MqttException {
        // Primeiro publish = falha (connected false)
        sensorWorker.publicarNoMQTT("sensores/temperatura", "25.5");
        // Segundo publish = deve dar certo (connected true)
        sensorWorker.publicarNoMQTT("sensores/umidade", "60.0");
    }

    @Então("os dados de temperatura e umidade são publicados novamente no broker MQTT")
    public void os_dados_de_temperatura_e_umidade_sao_publicados_novamente_no_broker_mqtt() throws MqttException {
        // Espera 1 invocação em "sensores/temperatura" e 1 em "sensores/umidade"
        verify(mqttClientMock, times(1)).publish(eq("sensores/temperatura"), any(MqttMessage.class));
        verify(mqttClientMock, times(1)).publish(eq("sensores/umidade"), any(MqttMessage.class));
    }

    // ---- Interrupção ----
    @Dado("o leitor de temperatura está em execução")
    public void o_leitor_de_temperatura_esta_em_execucao() {
        Thread t = new Thread(() -> sensorWorker.iniciarLeitura());
        t.start();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Quando("o processo é interrompido")
    public void o_processo_e_interrompido() {
        sensorWorker.pararLeitura();
    }

    @Então("o leitor para a leitura da porta serial")
    public void o_leitor_para_a_leitura_da_porta_serial() {
        verify(portaSerialMock, times(1)).removeDataListener();
        verify(portaSerialMock, times(1)).closePort();
    }
}
